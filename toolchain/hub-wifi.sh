#!/bin/sh
# Run a command against the Control Hub over its Wi-Fi, then put the laptop
# back on whatever Wi-Fi network it was on before (even on failure or Ctrl-C).
#
# The laptop has one Wi-Fi adapter, so joining the robot's network drops the
# normal one. Network switching runs on the host via NetworkManager over SSH as
# root; the command runs in the toolchain container (host networking) after
# adb is connected to the hub, with $HUB set to the hub's adb address.
#
# Usage (from the Makefile): hub-wifi.sh '<shell command to run in the container>'
# Environment: ROOT_SSH, HUB_SSID, HUB_ADDR, RUN, IMAGE (all set by the Makefile)

set -eu

hub_cmd="$1"

active_wifi() {
    ${ROOT_SSH} "nmcli -g NAME,TYPE connection show --active" |
        awk -F: '$2 == "802-11-wireless" { print $1; exit }'
}

switch_to() {
    echo "Wi-Fi: switching to $1"
    ${ROOT_SSH} "nmcli --wait 45 connection up id '$1'" >/dev/null
}

previous="$(active_wifi)"

restore() {
    status=$?
    trap - EXIT INT TERM

    if [ -n "${previous}" ] && [ "${previous}" != "${HUB_SSID}" ]; then
        switch_to "${previous}" || echo "Wi-Fi: could not reconnect to ${previous}; reconnect manually" >&2
    fi

    exit "${status}"
}
trap restore EXIT INT TERM

switch_to "${HUB_SSID}"

# The hub's adb daemon can take a few seconds to answer after the link comes up.
container_cmd="
    set -e
    HUB=${HUB_ADDR}
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        adb connect \$HUB | grep -q 'connected to' && break
        sleep 2
    done
    ${hub_cmd}
"

# RUN comes from the Makefile with its own quoting and \$(id -u), so it must be
# re-parsed by the shell; IMAGE and container_cmd stay single words.
eval "${RUN} \"\${IMAGE}\" sh -c \"\${container_cmd}\""

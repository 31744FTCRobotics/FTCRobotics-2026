package org.firstinspires.ftc.teamcode;

import static com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/*
 * Autonomous for the goBILDA® mecanum StarterBot (DECODE™), ported from goBILDA's two-wheel
 * StarterBotAuto to four mecanum wheels.
 *
 * The robot starts with its back against the goal. It launches three projectiles (skipped while
 * the launcher isn't in the robot configuration), backs away 4", turns 45° away from the goal
 * (left on RED, right on BLUE), then backs 26" off the starting line.
 *
 * During INIT: press X for BLUE or B for RED. The telemetry also shows the four drive encoders;
 * push the robot by hand and check that all four numbers change, because the moves below rely on
 * the drive motors' encoder cables being plugged in.
 *
 * Like goBILDA's version, this is a state machine: loop() runs over and over, each step starts
 * a move once and then checks every loop whether it has finished, so nothing ever blocks.
 */
@Autonomous(name = "StarterBotAutoMecanums", group = "StarterBot")
public class StarterBotAutoMecanums extends OpMode {
    final double FEED_TIME = 0.20; // The feeder servos run this long when a shot is requested.
    final double LAUNCHER_TARGET_VELOCITY = 1125;
    final double LAUNCHER_MIN_VELOCITY = 1075;
    final double TIME_BETWEEN_SHOTS = 2;
    final int SHOTS_TO_FIRE = 3;

    /*
     * The routine. Negative distances drive backward; positive angles turn left (counter-clockwise
     * seen from above). BLUE mirrors the turn.
     */
    final double BACK_AWAY_INCHES = -4;
    final double TURN_DEGREES = 45;
    final double LEAVE_LINE_INCHES = -26;

    final double DRIVE_SPEED = 0.5;
    final double ROTATE_SPEED = 0.3;

    /*
     * Encoder math for the goBILDA mecanum StarterBot: 104mm mecanum wheels on 19.2:1 Yellow
     * Jacket motors (537.7 encoder ticks per wheel revolution).
     */
    final double WHEEL_DIAMETER_MM = 104;
    final double ENCODER_TICKS_PER_REV = 537.7;
    final double TICKS_PER_MM = ENCODER_TICKS_PER_REV / (WHEEL_DIAMETER_MM * Math.PI);

    /*
     * When a mecanum robot spins in place, each wheel travels (TRACK_WIDTH + WHEEL_BASE) / 2 per
     * radian of turn. Measure your robot wheel-center to wheel-center: left to right is the track
     * width, front to back is the wheel base. If the 45° turn comes out short, increase these; if
     * it overshoots, decrease them.
     */
    final double TRACK_WIDTH_MM = 404;
    final double WHEEL_BASE_MM = 336;

    /*
     * A move is finished once every wheel is within the tolerance of its target and has stayed
     * there for the hold time. The timeout ends a move that can't finish (for example, an encoder
     * cable isn't plugged in, or the robot is pushing against something) so it can't run away.
     */
    final double TOLERANCE_MM = 10;
    final double HOLD_SECONDS = 0.25;
    final double MOVE_TIMEOUT_SECONDS = 4;

    private DcMotor leftFrontDrive = null;
    private DcMotor rightFrontDrive = null;
    private DcMotor leftBackDrive = null;
    private DcMotor rightBackDrive = null;
    private DcMotorEx launcher = null;
    private CRServo leftFeeder = null;
    private CRServo rightFeeder = null;
    private boolean hasLauncher = false; // true once launcher + both feeders are in the robot configuration

    private final ElapsedTime shotTimer = new ElapsedTime();
    private final ElapsedTime feederTimer = new ElapsedTime();
    private final ElapsedTime moveTimer = new ElapsedTime();
    private final ElapsedTime holdTimer = new ElapsedTime();

    private int shotsRemaining = SHOTS_TO_FIRE;
    private boolean moveTimedOut = false;

    private enum LaunchState {
        IDLE,
        PREPARE,
        LAUNCH,
    }

    private LaunchState launchState;

    private enum AutonomousState {
        LAUNCH,
        WAIT_FOR_LAUNCH,
        START_BACK_AWAY,
        BACKING_AWAY,
        START_ROTATE,
        ROTATING,
        START_LEAVE_LINE,
        LEAVING_LINE,
        COMPLETE,
    }

    private AutonomousState autonomousState;

    private enum Alliance {
        RED,
        BLUE,
    }

    private Alliance alliance = Alliance.RED;

    @Override
    public void init() {
        leftFrontDrive = hardwareMap.get(DcMotor.class, "left_front_drive");
        rightFrontDrive = hardwareMap.get(DcMotor.class, "right_front_drive");
        leftBackDrive = hardwareMap.get(DcMotor.class, "left_back_drive");
        rightBackDrive = hardwareMap.get(DcMotor.class, "right_back_drive");

        /*
         * The launcher isn't built yet, so its devices are optional: tryGet returns null when a
         * name is missing from the robot configuration, and the routine skips straight to driving.
         */
        launcher = hardwareMap.tryGet(DcMotorEx.class, "launcher");
        leftFeeder = hardwareMap.tryGet(CRServo.class, "left_feeder");
        rightFeeder = hardwareMap.tryGet(CRServo.class, "right_feeder");
        hasLauncher = launcher != null && leftFeeder != null && rightFeeder != null;

        // Same directions as StarterBotTeleopMecanums, so positive power drives forward.
        leftFrontDrive.setDirection(DcMotor.Direction.REVERSE);
        rightFrontDrive.setDirection(DcMotor.Direction.FORWARD);
        leftBackDrive.setDirection(DcMotor.Direction.REVERSE);
        rightBackDrive.setDirection(DcMotor.Direction.FORWARD);

        for (DcMotor motor : driveMotors()) {
            motor.setZeroPowerBehavior(BRAKE);
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        }

        if (hasLauncher) {
            launcher.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            launcher.setZeroPowerBehavior(BRAKE);
            launcher.setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER, new PIDFCoefficients(300, 0, 0, 10));
            leftFeeder.setDirection(DcMotorSimple.Direction.REVERSE);
        }

        launchState = LaunchState.IDLE;
        autonomousState = hasLauncher ? AutonomousState.LAUNCH : AutonomousState.START_BACK_AWAY;

        telemetry.addData("Status", "Initialized");
    }

    @Override
    public void init_loop() {
        if (hasLauncher) {
            leftFeeder.setPower(0);
            rightFeeder.setPower(0);
        }

        if (gamepad1.b) {
            alliance = Alliance.RED;
        } else if (gamepad1.x) {
            alliance = Alliance.BLUE;
        }

        telemetry.addData("Press X", "for BLUE");
        telemetry.addData("Press B", "for RED");
        telemetry.addData("Selected Alliance", alliance);
        telemetry.addData("Launcher", hasLauncher ? "found - will fire " + SHOTS_TO_FIRE : "not configured - drive only");
        telemetry.addData("Encoders (push robot to check)", "LF %d  RF %d  LB %d  RB %d",
                leftFrontDrive.getCurrentPosition(), rightFrontDrive.getCurrentPosition(),
                leftBackDrive.getCurrentPosition(), rightBackDrive.getCurrentPosition());
    }

    @Override
    public void start() {
        // Pushing the robot during INIT moved the encoders; start the routine from zero.
        for (DcMotor motor : driveMotors()) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        }
    }

    @Override
    public void loop() {
        switch (autonomousState) {
            case LAUNCH:
                launch(true);
                autonomousState = AutonomousState.WAIT_FOR_LAUNCH;
                break;

            case WAIT_FOR_LAUNCH:
                if (launch(false)) {
                    shotsRemaining -= 1;

                    if (shotsRemaining > 0) {
                        autonomousState = AutonomousState.LAUNCH;
                    } else {
                        launcher.setVelocity(0);
                        autonomousState = AutonomousState.START_BACK_AWAY;
                    }
                }
                break;

            case START_BACK_AWAY:
                startDrive(BACK_AWAY_INCHES, DistanceUnit.INCH);
                autonomousState = AutonomousState.BACKING_AWAY;
                break;

            case BACKING_AWAY:
                if (moveFinished()) {
                    autonomousState = AutonomousState.START_ROTATE;
                }
                break;

            case START_ROTATE:
                startRotate(alliance == Alliance.RED ? TURN_DEGREES : -TURN_DEGREES, AngleUnit.DEGREES);
                autonomousState = AutonomousState.ROTATING;
                break;

            case ROTATING:
                if (moveFinished()) {
                    autonomousState = AutonomousState.START_LEAVE_LINE;
                }
                break;

            case START_LEAVE_LINE:
                startDrive(LEAVE_LINE_INCHES, DistanceUnit.INCH);
                autonomousState = AutonomousState.LEAVING_LINE;
                break;

            case LEAVING_LINE:
                if (moveFinished()) {
                    autonomousState = AutonomousState.COMPLETE;
                }
                break;

            case COMPLETE:
                stopDrive();
                break;
        }

        telemetry.addData("AutoState", autonomousState);
        telemetry.addData("Alliance", alliance);

        if (hasLauncher) {
            telemetry.addData("LauncherState", launchState);
        }

        if (moveTimedOut) {
            telemetry.addData("WARNING", "a move timed out - check drive encoder cables");
        }

        telemetry.addData("Positions", "LF %d  RF %d  LB %d  RB %d",
                leftFrontDrive.getCurrentPosition(), rightFrontDrive.getCurrentPosition(),
                leftBackDrive.getCurrentPosition(), rightBackDrive.getCurrentPosition());
        telemetry.addData("Targets", "LF %d  RF %d  LB %d  RB %d",
                leftFrontDrive.getTargetPosition(), rightFrontDrive.getTargetPosition(),
                leftBackDrive.getTargetPosition(), rightBackDrive.getTargetPosition());
    }

    @Override
    public void stop() {
        stopDrive();
    }

    /*
     * Same shot sequence as goBILDA's StarterBotAuto: spin the flywheel up to speed, run the
     * feeders briefly, then wait out the time between shots. Returns true when a shot is done.
     */
    boolean launch(boolean shotRequested) {
        boolean shotComplete = false;

        switch (launchState) {
            case IDLE:
                if (shotRequested) {
                    launchState = LaunchState.PREPARE;
                    shotTimer.reset();
                }
                break;

            case PREPARE:
                launcher.setVelocity(LAUNCHER_TARGET_VELOCITY);

                if (launcher.getVelocity() > LAUNCHER_MIN_VELOCITY) {
                    launchState = LaunchState.LAUNCH;
                    leftFeeder.setPower(1);
                    rightFeeder.setPower(1);
                    feederTimer.reset();
                }
                break;

            case LAUNCH:
                if (feederTimer.seconds() > FEED_TIME) {
                    leftFeeder.setPower(0);
                    rightFeeder.setPower(0);

                    if (shotTimer.seconds() > TIME_BETWEEN_SHOTS) {
                        launchState = LaunchState.IDLE;
                        shotComplete = true;
                    }
                }
                break;
        }

        return shotComplete;
    }

    /*
     * Drive straight forward (positive) or backward (negative): all four wheels to the same target.
     */
    void startDrive(double distance, DistanceUnit distanceUnit) {
        int ticks = (int) Math.round(distanceUnit.toMm(distance) * TICKS_PER_MM);

        startMove(ticks, ticks, ticks, ticks, DRIVE_SPEED);
    }

    /*
     * Spin in place: positive angles turn left, so the left wheels go backward and the right
     * wheels forward.
     */
    void startRotate(double angle, AngleUnit angleUnit) {
        double wheelTravelMm = angleUnit.toRadians(angle) * (TRACK_WIDTH_MM + WHEEL_BASE_MM) / 2;
        int ticks = (int) Math.round(wheelTravelMm * TICKS_PER_MM);

        startMove(-ticks, ticks, -ticks, ticks, ROTATE_SPEED);
    }

    /*
     * Start a move once; moveFinished() then reports when it's done. Encoders are reset first,
     * so every target is relative to where the robot is now.
     */
    void startMove(int leftFront, int rightFront, int leftBack, int rightBack, double speed) {
        for (DcMotor motor : driveMotors()) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        }

        leftFrontDrive.setTargetPosition(leftFront);
        rightFrontDrive.setTargetPosition(rightFront);
        leftBackDrive.setTargetPosition(leftBack);
        rightBackDrive.setTargetPosition(rightBack);

        for (DcMotor motor : driveMotors()) {
            motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            motor.setPower(speed);
        }

        moveTimer.reset();
        holdTimer.reset();
    }

    boolean moveFinished() {
        boolean allOnTarget = true;

        for (DcMotor motor : driveMotors()) {
            int error = Math.abs(motor.getTargetPosition() - motor.getCurrentPosition());

            if (error > TOLERANCE_MM * TICKS_PER_MM) {
                allOnTarget = false;
            }
        }

        if (!allOnTarget) {
            holdTimer.reset();
        }

        boolean timedOut = moveTimer.seconds() > MOVE_TIMEOUT_SECONDS;
        moveTimedOut |= timedOut;

        boolean finished = holdTimer.seconds() > HOLD_SECONDS || timedOut;

        if (finished) {
            stopDrive();
        }

        return finished;
    }

    void stopDrive() {
        for (DcMotor motor : driveMotors()) {
            motor.setPower(0);
        }
    }

    DcMotor[] driveMotors() {
        return new DcMotor[] {leftFrontDrive, rightFrontDrive, leftBackDrive, rightBackDrive};
    }
}

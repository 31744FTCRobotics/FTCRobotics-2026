package org.firstinspires.ftc.teamcode;

import static com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

/*
 * DriveOutAndBack: drives straight forward a set distance, pauses, then drives back to where it
 * started.
 *
 * The distance starts at DEFAULT_DISTANCE_INCHES and can be changed during INIT with the D-pad
 * (up = farther, down = shorter), so you can try different distances without rebuilding.
 *
 * It measures distance with the drive motors' encoders (RUN_TO_POSITION), so the encoder cables
 * must be plugged in next to each motor port. If the encoders don't count, it stops right away and
 * says so on the Driver Hub instead of driving off.
 *
 * This is a LinearOpMode: runOpMode() reads top to bottom, like a recipe. waitForStart()-style
 * code is easier to follow than a state machine for simple sequences like this one.
 */
@Autonomous(name = "DriveOutAndBack", group = "Test")
public class DriveOutAndBack extends LinearOpMode {
    /*
     * Motor names from the robot configuration (StarterBotMecanumExpanded). If you activate a
     * configuration with different names, change these to match.
     */
    static final String LEFT_FRONT_NAME = "frontleftmotor";
    static final String RIGHT_FRONT_NAME = "frontrightmotor";
    static final String LEFT_BACK_NAME = "backleftmotor";
    static final String RIGHT_BACK_NAME = "backrightmotor";

    // How far to go out, and the limits for changing it with the D-pad during INIT.
    static final double DEFAULT_DISTANCE_INCHES = 24;
    static final double DISTANCE_STEP_INCHES = 6;
    static final double MIN_DISTANCE_INCHES = 6;
    static final double MAX_DISTANCE_INCHES = 96;

    static final double DRIVE_SPEED = 0.4;     // 0 to 1; slower is more accurate
    static final double PAUSE_SECONDS = 1.0;   // wait at the far end before coming back

    /*
     * Encoder math for the goBILDA mecanum StarterBot: 104mm wheels on 19.2:1 Yellow Jacket
     * motors, which count 537.7 encoder ticks per wheel revolution.
     */
    static final double WHEEL_DIAMETER_MM = 104;
    static final double ENCODER_TICKS_PER_REV = 537.7;
    static final double TICKS_PER_INCH = ENCODER_TICKS_PER_REV / (WHEEL_DIAMETER_MM * Math.PI) * 25.4;

    /*
     * Safety: if the encoders haven't counted at least a few ticks shortly after the motors start,
     * the cables are probably unplugged, so stop before the robot drives off. Any single move that
     * takes longer than the timeout is also stopped.
     */
    static final double ENCODER_CHECK_SECONDS = 0.25;
    static final int ENCODER_CHECK_MIN_TICKS = 20;
    static final double MOVE_TIMEOUT_SECONDS = 6;

    private DcMotor leftFront;
    private DcMotor rightFront;
    private DcMotor leftBack;
    private DcMotor rightBack;
    private DcMotor[] driveMotors;

    @Override
    public void runOpMode() {
        leftFront = hardwareMap.get(DcMotor.class, LEFT_FRONT_NAME);
        rightFront = hardwareMap.get(DcMotor.class, RIGHT_FRONT_NAME);
        leftBack = hardwareMap.get(DcMotor.class, LEFT_BACK_NAME);
        rightBack = hardwareMap.get(DcMotor.class, RIGHT_BACK_NAME);
        driveMotors = new DcMotor[] {leftFront, rightFront, leftBack, rightBack};

        // Same directions as StarterBotTeleopMecanums, so positive power drives forward.
        leftFront.setDirection(DcMotor.Direction.REVERSE);
        rightFront.setDirection(DcMotor.Direction.FORWARD);
        leftBack.setDirection(DcMotor.Direction.REVERSE);
        rightBack.setDirection(DcMotor.Direction.FORWARD);

        for (DcMotor motor : driveMotors) {
            motor.setZeroPowerBehavior(BRAKE);
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        }

        double distanceInches = DEFAULT_DISTANCE_INCHES;

        // INIT: until START is pressed, let the driver adjust the distance.
        while (opModeInInit()) {
            if (gamepad1.dpadUpWasPressed()) {
                distanceInches = Math.min(distanceInches + DISTANCE_STEP_INCHES, MAX_DISTANCE_INCHES);
            } else if (gamepad1.dpadDownWasPressed()) {
                distanceInches = Math.max(distanceInches - DISTANCE_STEP_INCHES, MIN_DISTANCE_INCHES);
            }

            telemetry.addData("Distance", "%.0f in (D-pad up/down to change)", distanceInches);
            telemetry.addData("Encoders (push robot to check)", positionsText());
            telemetry.update();
        }

        // START was pressed (or STOP; then opModeIsActive() is false and we skip everything).
        boolean ok = opModeIsActive() && driveInches(distanceInches, "Driving out");

        if (ok) {
            sleep((long) (PAUSE_SECONDS * 1000));
            ok = opModeIsActive() && driveInches(-distanceInches, "Driving back");
        }

        // Keep the result on the Driver Hub screen until STOP is pressed.
        while (opModeIsActive()) {
            telemetry.addData("Result", ok ? "Done - back at the start" : "Stopped early (see Problem)");
            telemetry.update();
            sleep(100);
        }
    }

    /*
     * Drive straight forward (positive inches) or backward (negative), then stop. Returns true if
     * the move finished normally.
     */
    boolean driveInches(double inches, String label) {
        int target = (int) Math.round(inches * TICKS_PER_INCH);

        // Each move starts from zero, so the target is "this far from here".
        for (DcMotor motor : driveMotors) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            motor.setTargetPosition(target);
            motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            motor.setPower(DRIVE_SPEED);
        }

        ElapsedTime timer = new ElapsedTime();
        String problem = "";

        // RUN_TO_POSITION makes each motor stop itself at the target; isBusy() is true until then.
        while (opModeIsActive() && problem.isEmpty() && anyMotorBusy()) {
            if (timer.seconds() > ENCODER_CHECK_SECONDS && largestPosition() < ENCODER_CHECK_MIN_TICKS) {
                problem = "Encoders aren't counting - check the drive motor encoder cables";
            } else if (timer.seconds() > MOVE_TIMEOUT_SECONDS) {
                problem = "Move timed out - is something blocking the robot?";
            }

            telemetry.addData("Step", label);
            telemetry.addData("Target", "%d ticks (%.0f in)", target, inches);
            telemetry.addData("Positions", positionsText());
            telemetry.update();
        }

        for (DcMotor motor : driveMotors) {
            motor.setPower(0);
        }

        if (!problem.isEmpty()) {
            telemetry.addData("Problem", problem);
            telemetry.update();
        }

        return problem.isEmpty() && opModeIsActive();
    }

    boolean anyMotorBusy() {
        boolean busy = false;

        for (DcMotor motor : driveMotors) {
            busy |= motor.isBusy();
        }

        return busy;
    }

    int largestPosition() {
        int largest = 0;

        for (DcMotor motor : driveMotors) {
            largest = Math.max(largest, Math.abs(motor.getCurrentPosition()));
        }

        return largest;
    }

    String positionsText() {
        return String.format("LF %d  RF %d  LB %d  RB %d",
                leftFront.getCurrentPosition(), rightFront.getCurrentPosition(),
                leftBack.getCurrentPosition(), rightBack.getCurrentPosition());
    }
}

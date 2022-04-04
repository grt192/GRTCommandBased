package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.SupplyCurrentLimitConfiguration;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMax.SoftLimitDirection;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

import edu.wpi.first.networktables.EntryListenerFlags;
import edu.wpi.first.networktables.EntryNotification;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandBase;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitUntilCommand;

import frc.robot.GRTSubsystem;
import frc.robot.brownout.PowerController;
import frc.robot.shuffleboard.GRTNetworkTableEntry;
import frc.robot.shuffleboard.GRTShuffleboardTab;

import static frc.robot.Constants.ClimbConstants.*;

public class ClimbSubsystem extends GRTSubsystem {
    private final CANSparkMax six;
    private final RelativeEncoder sixEncoder;
    private final SparkMaxPIDController sixPidController;
    private final WPI_TalonSRX sixBrake;

    /*
    private final CANSparkMax ten;
    private final RelativeEncoder tenEncoder;
    private final SparkMaxPIDController tenPidController;
    private final WPI_TalonSRX tenBrake;

    private final WPI_TalonSRX tenSolenoidMain;
    private final WPI_TalonSRX tenSolenoidFollow;

    private final WPI_TalonSRX fifteenMain;
    private final WPI_TalonSRX fifteenFollow;
    */

    // Six point arm smart motion PID constants
    private static final double sixP = 0;
    private static final double sixI = 0;
    private static final double sixD = 0;
    private static final double sixFF = 0.000125;
    private static final double maxVel = 150;
    private static final double maxAccel = 300;

    private static final double SIX_MIN_POS = 0;
    private static final double SIX_MAX_POS = 208.7743377685547;
    private static final double SIX_RETRACTED_POS = 50;

    // Ten point arm position PID constants
    private static final double tenP = 0.125;
    private static final double tenI = 0;
    private static final double tenD = 0;

    // Temp states for brake toggles
    private boolean sixBrakeEngaged = false;
    private boolean lastRetracted = false;
    private boolean retractToExtend = false;
    private boolean brakeLastEngaged = true;
    private Timer brakeSwitch;

    // Fifteen point arm position PID constants
    private static final double fifteenP = 0.125;
    private static final double fifteenI = 0;
    private static final double fifteenD = 0;

    private final GRTShuffleboardTab shuffleboardTab;
    private final GRTNetworkTableEntry sixPosEntry;

    // Debug flags
    // Whether PID tuning shuffleboard entries should be displayed
    private static boolean DEBUG_PID = true;

    public ClimbSubsystem() {
        // TODO: measure this
        super(20);

        // Initialize six point arm NEO, encoder PID, and solenoid brake
        six = new CANSparkMax(sixMotorPort, MotorType.kBrushless);
        six.restoreFactoryDefaults();
        six.setIdleMode(IdleMode.kBrake);
        six.setInverted(true);
        brakeSwitch = new Timer();

        lastRetracted = true;

        six.setSoftLimit(SoftLimitDirection.kForward, (float) SIX_MAX_POS);
        //six.setSoftLimit(SoftLimitDirection.kReverse, (float) SIX_MIN_POS);
        //six.enableSoftLimit(SoftLimitDirection.kForward, true);
        //six.enableSoftLimit(SoftLimitDirection.kReverse, true);

        sixEncoder = six.getEncoder();
        sixEncoder.setPosition(0);

        sixPidController = six.getPIDController();
        sixPidController.setP(sixP);
        sixPidController.setI(sixI);
        sixPidController.setD(sixD);

        sixBrake = new WPI_TalonSRX(sixBrakePort);
        sixBrake.configFactoryDefault();

        // Initialize ten point arm NEO, encoder PID, and solenoid brake
        /*
        ten = new CANSparkMax(tenMotorPort, MotorType.kBrushless);
        ten.restoreFactoryDefaults();
        ten.setIdleMode(IdleMode.kBrake);

        tenEncoder = ten.getEncoder();
        tenEncoder.setPosition(0);

        tenPidController = ten.getPIDController();
        tenPidController.setP(tenP);
        tenPidController.setI(tenI);
        tenPidController.setD(tenD);

        tenBrake = new WPI_TalonSRX(tenBrakePort);
        tenBrake.configFactoryDefault();

        // Initialize ten point arm solenoid release mechanisms
        tenSolenoidMain = new WPI_TalonSRX(tenLeftSolenoidPort);
        tenSolenoidMain.configFactoryDefault();

        tenSolenoidFollow = new WPI_TalonSRX(tenRightSolenoidPort);
        tenSolenoidFollow.configFactoryDefault();
        tenSolenoidFollow.follow(tenSolenoidMain);

        // Initialize fifteen point arm 775, encoder PID, and follower
        fifteenMain = new WPI_TalonSRX(fifteenLeftPort);
        fifteenMain.configFactoryDefault();
        fifteenMain.setNeutralMode(NeutralMode.Brake);

        fifteenMain.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, 0, 0);
        fifteenMain.setSelectedSensorPosition(0);
        fifteenMain.setSensorPhase(false);
        fifteenMain.config_kP(0, fifteenP);
        fifteenMain.config_kI(0, fifteenI);
        fifteenMain.config_kD(0, fifteenD);

        fifteenFollow = new WPI_TalonSRX(fifteenRightPort);
        fifteenFollow.configFactoryDefault();
        fifteenFollow.follow(fifteenMain);
        fifteenMain.setNeutralMode(NeutralMode.Brake);
        */

        // Initialize Shuffleboard entries
        shuffleboardTab = new GRTShuffleboardTab("Climb");
        sixPosEntry = shuffleboardTab.addEntry("Six pos", sixEncoder.getPosition());

        // If DEBUG_PID is set, allow for PID tuning on shuffleboard
        if (DEBUG_PID) {
            shuffleboardTab
                .list("Six PID")
                .addListener("kP", sixP, this::setSixP)
                .addListener("kI", sixI, this::setSixI)
                .addListener("kD", sixD, this::setSixD)
                .addListener("kFF", sixFF, this::setSixFF)
                .addListener("Max vel", maxVel, this::setSixMaxVel)
                .addListener("Max accel", maxAccel, this::setSixMaxAccel);
        }
    }

    @Override
    public void periodic() {
        sixPosEntry.setValue(sixEncoder.getPosition());
    }

    /**
     * Manually sets the six winch power.
     * @param pow The power to set.
     */
    public void setSixPower(double pow) {
      /*
        double power = pow;
        if (power > 0) {
            lastRetracted = true;
        }
        six.set(pow);
        */

        double power = pow;
        if (power < 0) {
            lastRetracted = true;
        }
        
        if (lastRetracted && brakeLastEngaged && (power > 0)) {
            System.out.println();
            brakeSwitch.start();
            lastRetracted = false;
            power = -0.07;
            retractToExtend = true;
        } else if (retractToExtend) {
            power = -0.07;
        }

        //stop retracting and start extending
        if (retractToExtend && brakeSwitch.hasElapsed(0.15)) {
            sixBrake.set(1);
            sixBrakeEngaged = false;
            brakeLastEngaged = false;
            retractToExtend = false;
            brakeSwitch.stop();
            brakeSwitch.reset();
        }
        
        // set power and brake mode
        six.set(power);
        // brake engaged -> not powered, disengaged -> powered
        sixBrake.set(sixBrakeEngaged ? 0 : 1);
    }

    /**
     * Manually sets the six brake status. When the brake is disengaged, 
     * the solenoid is powered (retracted).
     * @param brake Whether to engage the brake.
     */
    public void setSixBrake(boolean brake) {
        sixBrakeEngaged = brake;
        if (brake) {
            brakeLastEngaged = true;
        }
       // sixBrakeEngaged = !sixBrakeEngaged;
    }

    /**
     * PHASE 1 (6 point rung)
     * TODO: measure these positions (for all phases)
     */
    public class ClimbPhase1Command extends CommandBase {
        @Override
        public void initialize() {
            // Disengage the six point arm brake and extend the arm to grab the rung
            sixBrake.set(0);
            sixPidController.setReference(SIX_MAX_POS, ControlType.kSmartMotion);
        }

        @Override
        public boolean isFinished() {
            return Math.abs(sixEncoder.getPosition() - SIX_MAX_POS) < 1;
        }

        @Override
        public void end(boolean interrupted) {
            // Engage the brake and partially retract the arm after grabbing
            sixBrake.set(1);
            sixPidController.setReference(SIX_RETRACTED_POS, ControlType.kSmartMotion);
        }
    }

    /**
     * Gets the climb sequence as a SequentialCommandGroup.
     * @return The command group representing the climb sequence.
     */
    public Command climb(GRTSubsystem... subsystems) {
        for (GRTSubsystem subsystem : subsystems) subsystem.climbInit();
        return new ClimbPhase1Command();
    }

    @Override
    public double getTotalCurrentDrawn() {
        return PowerController.getCurrentDrawnFromPDH(
            sixMotorPort, sixBrakePort, tenMotorPort, tenBrakePort, 
            fifteenLeftPort, fifteenRightPort, tenLeftSolenoidPort, tenRightSolenoidPort
        );
    }

    @Override
    public void setCurrentLimit(double limit) {
        int motorLimit = (int) Math.floor(limit);

        six.setSmartCurrentLimit(motorLimit);
        // ten.setSmartCurrentLimit(motorLimit);
        // fifteenMain.configSupplyCurrentLimit(new SupplyCurrentLimitConfiguration(true, motorLimit, 0, 0));
    }

    /**
     * Climb PID tuning NetworkTable callbacks.
     * @param change The `EntryNotification` representing the NetworkTable entry change.
     */
    private void setSixP(EntryNotification change) {
        sixPidController.setP(change.value.getDouble());
    }

    private void setSixI(EntryNotification change) {
        sixPidController.setI(change.value.getDouble());
    }

    private void setSixD(EntryNotification change) {
        sixPidController.setD(change.value.getDouble());
    }

    private void setSixFF(EntryNotification change) {
        sixPidController.setFF(change.value.getDouble());
    }

    private void setSixMaxVel(EntryNotification change) {
        sixPidController.setSmartMotionMaxVelocity(change.value.getDouble(), 0);
    }

    private void setSixMaxAccel(EntryNotification change) {
        sixPidController.setSmartMotionMaxAccel(change.value.getDouble(), 0);
    }
}

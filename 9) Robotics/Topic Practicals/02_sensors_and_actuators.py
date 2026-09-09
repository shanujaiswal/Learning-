"""
02 - Sensors and Actuators: Noisy Sensing, Filtering, and Motor Response
=========================================================================

Companion practical for Theory/02 Sensors and Actuators.md

This script simulates the "sense" and "act" halves of the robot loop:

Sensors (exteroceptive + proprioceptive):
    - An ultrasonic/IR-style distance sensor: returns a noisy reading of
      the true distance to an obstacle, with occasional spurious "echo"
      outliers (the specular-reflection problem described in the theory).
    - A wheel encoder: counts discrete ticks as a wheel turns, giving a
      quantized (and therefore imperfect) estimate of distance traveled
      (basic wheel odometry).
    - An IMU-style gyro: a noisy angular velocity reading that, when
      naively integrated, drifts away from the true heading over time --
      exactly the drift problem described in the theory.

Filtering (never trust one raw reading):
    - A simple moving-average filter cleans up the noisy distance sensor.
    - A complementary filter blends a drifting integrated-gyro heading
      estimate with a noisy-but-driftless "absolute" heading measurement
      (standing in for a magnetometer), the classic cheap sensor-fusion
      technique used before reaching for a full Kalman filter.

Actuators:
    - A simple first-order DC motor model: commanded voltage doesn't
      instantly become velocity -- the motor accelerates towards a target
      speed with some inertia/time-constant, and never quite tracks a
      command perfectly instantaneously.
    - A servo model: given an angle command, it moves toward that angle
      at a bounded slew rate (models real physical travel time).

Run:
    pip install numpy matplotlib
    python 02_sensors_and_actuators.py
"""

import numpy as np
import matplotlib.pyplot as plt

RNG = np.random.default_rng(42)

# ---------------------------------------------------------------------------
# SENSOR MODELS
# ---------------------------------------------------------------------------


def ultrasonic_reading(true_distance, noise_std=0.03, outlier_prob=0.05,
                        outlier_scale=0.6):
    """Simulate one noisy ultrasonic/IR distance reading (meters).

    Most readings are true_distance + small Gaussian noise. Occasionally
    (outlier_prob) we get a spurious echo/reflection that reports a
    wildly different distance -- the "specular reflection" failure mode
    described in the theory.
    """
    reading = true_distance + RNG.normal(0, noise_std)
    if RNG.random() < outlier_prob:
        reading += RNG.normal(0, outlier_scale) * RNG.choice([-1, 1])
    return max(reading, 0.0)


def moving_average_filter(readings, window=5):
    """Simple moving-average low-pass filter over a sequence of readings."""
    readings = np.asarray(readings, dtype=float)
    kernel = np.ones(window) / window
    # 'same' padding via manual causal average avoids look-ahead bias
    filtered = np.zeros_like(readings)
    for i in range(len(readings)):
        lo = max(0, i - window + 1)
        filtered[i] = readings[lo:i + 1].mean()
    return filtered


def encoder_ticks(distance, ticks_per_meter=360):
    """Simulate a wheel encoder: distance -> quantized tick count -> distance.

    Encoders can only resolve position to the nearest tick, so this
    round-trip introduces quantization error, as described in the theory.
    """
    true_ticks = distance * ticks_per_meter
    measured_ticks = np.round(true_ticks)
    estimated_distance = measured_ticks / ticks_per_meter
    return int(measured_ticks), estimated_distance


def gyro_reading(true_angular_velocity, noise_std=0.02, bias=0.01):
    """Simulate a noisy gyroscope angular-velocity reading (rad/s).

    `bias` is a small constant offset -- real MEMS gyros have a non-zero
    bias that, once integrated over time, is the direct cause of drift.
    """
    return true_angular_velocity + bias + RNG.normal(0, noise_std)


def complementary_filter(gyro_heading_estimate, absolute_heading_measurement,
                          alpha=0.98):
    """Blend a drifting-but-smooth gyro estimate with a noisy-but-driftless
    absolute measurement (e.g. magnetometer/compass-derived heading).

    alpha close to 1 trusts the gyro's short-term smoothness; (1 - alpha)
    slowly pulls the estimate back towards the noisy-but-unbiased absolute
    measurement, correcting drift without reintroducing all of the noise.
    """
    return alpha * gyro_heading_estimate + (1 - alpha) * absolute_heading_measurement


# ---------------------------------------------------------------------------
# ACTUATOR MODELS
# ---------------------------------------------------------------------------


class DCMotor:
    """First-order DC motor model: velocity chases a commanded target
    velocity with a time constant `tau` (bigger tau = sluggish motor).
    """

    def __init__(self, tau=0.3):
        self.tau = tau
        self.velocity = 0.0

    def step(self, commanded_velocity, dt):
        # First-order ODE: dv/dt = (v_command - v) / tau
        self.velocity += (commanded_velocity - self.velocity) / self.tau * dt
        return self.velocity


class ServoMotor:
    """Servo model: moves toward a commanded angle at a bounded slew rate
    (deg/s), modelling the fact that a servo takes real time to travel.
    """

    def __init__(self, max_slew_rate_deg_s=180.0):
        self.angle = 0.0
        self.max_slew_rate = max_slew_rate_deg_s

    def step(self, commanded_angle, dt):
        max_delta = self.max_slew_rate * dt
        delta = np.clip(commanded_angle - self.angle, -max_delta, max_delta)
        self.angle += delta
        return self.angle


# ---------------------------------------------------------------------------
# DEMO
# ---------------------------------------------------------------------------


def main():
    print("=" * 70)
    print("SENSORS AND ACTUATORS DEMO")
    print("=" * 70)

    # -----------------------------------------------------------------
    # Demo 1: Ultrasonic distance sensor + moving-average filter
    # -----------------------------------------------------------------
    print("\n--- Ultrasonic distance sensor: raw noise vs filtered ---")
    true_distance = 2.0  # meters, a stationary obstacle
    n_samples = 60
    raw_readings = [ultrasonic_reading(true_distance) for _ in range(n_samples)]
    filtered_readings = moving_average_filter(raw_readings, window=7)

    raw_rmse = np.sqrt(np.mean((np.array(raw_readings) - true_distance) ** 2))
    filtered_rmse = np.sqrt(np.mean((filtered_readings - true_distance) ** 2))
    print(f"True distance:        {true_distance:.3f} m")
    print(f"Raw sensor RMSE:      {raw_rmse:.4f} m  (over {n_samples} samples)")
    print(f"Filtered RMSE:        {filtered_rmse:.4f} m")
    assert filtered_rmse < raw_rmse, "Filtering should reduce RMSE vs raw noise"
    print("Filtering reduced RMSE, as expected.")

    # -----------------------------------------------------------------
    # Demo 2: Wheel encoder odometry (quantization error)
    # -----------------------------------------------------------------
    print("\n--- Wheel encoder odometry ---")
    for true_dist in [0.10, 0.5017, 1.333]:
        ticks, est_dist = encoder_ticks(true_dist)
        print(f"true={true_dist:.4f} m -> ticks={ticks:4d} -> "
              f"estimated={est_dist:.4f} m  (quantization err="
              f"{abs(true_dist - est_dist):.5f} m)")

    # -----------------------------------------------------------------
    # Demo 3: Gyro integration drift vs complementary filter correction
    # -----------------------------------------------------------------
    print("\n--- Gyro heading: raw integration drift vs complementary filter ---")
    dt = 0.05
    steps = 200
    true_angular_velocity = 0.0  # robot is actually standing still (heading fixed)
    true_heading = 0.0

    raw_integrated_heading = 0.0
    comp_filtered_heading = 0.0

    raw_headings, comp_headings, true_headings = [], [], []
    for _ in range(steps):
        # Sensor readings
        gyro = gyro_reading(true_angular_velocity)  # biased + noisy
        # Absolute heading measurement (e.g. magnetometer): noisy but unbiased
        absolute_measurement = true_heading + RNG.normal(0, 0.15)

        # Naive raw integration (no correction) -- will drift due to gyro bias
        raw_integrated_heading += gyro * dt

        # Complementary filter: integrate gyro short-term, pull toward
        # the noisy absolute measurement long-term
        comp_filtered_heading = complementary_filter(
            comp_filtered_heading + gyro * dt, absolute_measurement, alpha=0.98)

        raw_headings.append(raw_integrated_heading)
        comp_headings.append(comp_filtered_heading)
        true_headings.append(true_heading)

    raw_final_error = abs(raw_headings[-1] - true_headings[-1])
    comp_final_error = abs(comp_headings[-1] - true_headings[-1])
    print(f"True heading (held constant at 0 rad throughout)")
    print(f"Raw integrated-gyro final error:   {raw_final_error:.4f} rad "
          f"(drifted away due to constant gyro bias)")
    print(f"Complementary-filter final error:  {comp_final_error:.4f} rad")
    assert comp_final_error < raw_final_error, \
        "Complementary filter should bound drift better than raw integration"
    print("Complementary filter kept heading estimate bounded, as expected.")

    # -----------------------------------------------------------------
    # Demo 4: DC motor and servo actuator step responses
    # -----------------------------------------------------------------
    print("\n--- Actuator step responses ---")
    motor = DCMotor(tau=0.3)
    servo = ServoMotor(max_slew_rate_deg_s=180.0)

    sim_time = 2.0
    n_steps = int(sim_time / dt)
    motor_velocities, servo_angles, times = [], [], []
    for i in range(n_steps):
        t = i * dt
        motor_velocities.append(motor.step(commanded_velocity=1.0, dt=dt))
        servo_angles.append(servo.step(commanded_angle=90.0, dt=dt))
        times.append(t)

    print(f"DC motor velocity after {sim_time:.1f}s (target=1.0): "
          f"{motor_velocities[-1]:.4f}")
    print(f"Servo angle after {sim_time:.1f}s (target=90 deg):    "
          f"{servo_angles[-1]:.2f} deg")
    assert motor_velocities[-1] > 0.95, "Motor should have nearly reached target"
    assert abs(servo_angles[-1] - 90.0) < 1e-6, "Servo should have reached target angle"
    print("Both actuators converged toward their commanded targets, as expected.")

    # -----------------------------------------------------------------
    # Plot everything
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(2, 2, figsize=(13, 9))
    fig.suptitle("Sensors and Actuators: Noise, Filtering, and Actuator Response")

    ax = axes[0, 0]
    ax.plot(raw_readings, "o-", color="tab:red", alpha=0.5, markersize=4, label="raw")
    ax.plot(filtered_readings, "-", color="tab:blue", linewidth=2, label="moving avg")
    ax.axhline(true_distance, color="black", linestyle="--", label="true distance")
    ax.set_title("Ultrasonic distance sensor")
    ax.set_xlabel("sample")
    ax.set_ylabel("distance (m)")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)

    ax = axes[0, 1]
    ax.plot(np.arange(steps) * dt, raw_headings, color="tab:red", label="raw integrated gyro (drifts)")
    ax.plot(np.arange(steps) * dt, comp_headings, color="tab:blue", label="complementary filter")
    ax.axhline(0, color="black", linestyle="--", label="true heading")
    ax.set_title("Gyro heading: drift vs complementary filter")
    ax.set_xlabel("time (s)")
    ax.set_ylabel("heading (rad)")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)

    ax = axes[1, 0]
    ax.plot(times, motor_velocities, color="tab:green")
    ax.axhline(1.0, color="black", linestyle="--", label="target velocity")
    ax.set_title("DC motor step response (first-order lag)")
    ax.set_xlabel("time (s)")
    ax.set_ylabel("velocity")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)

    ax = axes[1, 1]
    ax.plot(times, servo_angles, color="tab:purple")
    ax.axhline(90.0, color="black", linestyle="--", label="target angle")
    ax.set_title("Servo step response (bounded slew rate)")
    ax.set_xlabel("time (s)")
    ax.set_ylabel("angle (deg)")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)

    plt.tight_layout()
    out_path = "sensors_actuators_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()

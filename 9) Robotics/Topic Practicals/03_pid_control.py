"""
03 - Control Systems and PID Controllers
==========================================

Companion practical for Theory/03 Control Systems and PID Controllers.md

This script implements a PID controller from scratch (matching the
theory's Python listing, generalized to a fixed simulation dt rather
than wall-clock time so results are reproducible) and uses it to drive
a simple simulated plant: a DC-motor-driven joint that must reach and
hold a target angle, starting from rest, under a constant disturbance
torque (e.g. gravity acting on an arm) -- exactly the scenario the
theory uses to motivate the I term.

We compare four controllers on the identical plant and disturbance:
    1. P-only            -- shows persistent steady-state error
    2. PI                -- shows steady-state error eliminated
    3. PID (well tuned)  -- fast, small overshoot, well damped
    4. PID (aggressive)  -- high Kp, low Kd -> overshoot and oscillation

Run:
    pip install numpy matplotlib
    python 03_pid_control.py
"""

import numpy as np
import matplotlib.pyplot as plt


# ---------------------------------------------------------------------------
# PID Controller (discrete-time version of the theory's implementation)
# ---------------------------------------------------------------------------


class PIDController:
    """Textbook PID controller, discretized with a fixed timestep `dt`
    (instead of wall-clock time) so simulation results are deterministic.

    output = Kp*error + Ki*integral(error) + Kd*d(error)/dt

    Includes basic integral clamping to guard against unbounded windup,
    as flagged in the theory (`integral windup`).
    """

    def __init__(self, kp, ki, kd, setpoint, integral_limit=50.0):
        self.kp, self.ki, self.kd = kp, ki, kd
        self.setpoint = setpoint
        self.integral = 0.0
        self.prev_error = 0.0
        self.integral_limit = integral_limit
        self._first_update = True

    def update(self, measured_value, dt):
        error = self.setpoint - measured_value
        self.integral += error * dt
        self.integral = np.clip(self.integral, -self.integral_limit, self.integral_limit)

        if self._first_update:
            derivative = 0.0  # no previous error to differentiate against yet
            self._first_update = False
        else:
            derivative = (error - self.prev_error) / dt

        output = (self.kp * error) + (self.ki * self.integral) + (self.kd * derivative)
        self.prev_error = error
        return output


# ---------------------------------------------------------------------------
# Plant: a simple rotational joint with inertia, viscous damping, and a
# constant disturbance torque (e.g. gravity pulling the arm down)
# ---------------------------------------------------------------------------


class JointPlant:
    """angle'' = (motor_torque - damping*angle' - disturbance_torque) / inertia

    A simple second-order system: the controller's output is treated as
    an applied torque, and the joint has inertia and viscous friction.
    """

    def __init__(self, inertia=1.0, damping=0.8, disturbance_torque=1.5):
        self.inertia = inertia
        self.damping = damping
        self.disturbance_torque = disturbance_torque
        self.angle = 0.0
        self.angular_velocity = 0.0

    def step(self, motor_torque, dt):
        angular_accel = (motor_torque - self.damping * self.angular_velocity
                          - self.disturbance_torque) / self.inertia
        self.angular_velocity += angular_accel * dt
        self.angle += self.angular_velocity * dt
        return self.angle


def run_simulation(kp, ki, kd, setpoint=90.0, sim_time=35.0, dt=0.02):
    """Run one closed-loop simulation and return the time/angle/command traces."""
    pid = PIDController(kp, ki, kd, setpoint)
    plant = JointPlant()

    n_steps = int(sim_time / dt)
    times = np.zeros(n_steps)
    angles = np.zeros(n_steps)
    commands = np.zeros(n_steps)

    for i in range(n_steps):
        t = i * dt
        command = pid.update(plant.angle, dt)
        angle = plant.step(command, dt)
        times[i] = t
        angles[i] = angle
        commands[i] = command

    return times, angles, commands


def analyze_response(times, angles, setpoint, settle_band=0.02):
    """Compute steady-state error, overshoot %, and rough settling time."""
    final_value = angles[-1]
    steady_state_error = setpoint - final_value

    peak = angles.max() if setpoint > 0 else angles.min()
    overshoot_pct = max(0.0, (peak - setpoint) / setpoint * 100) if setpoint != 0 else 0.0

    band = settle_band * abs(setpoint)
    settled_mask = np.abs(angles - setpoint) <= band
    settling_time = None
    # find the last time the signal leaves the band -> settling time is just after that
    outside = np.where(~settled_mask)[0]
    if len(outside) > 0 and outside[-1] < len(times) - 1:
        settling_time = times[outside[-1] + 1]
    elif len(outside) == 0:
        settling_time = times[0]

    return steady_state_error, overshoot_pct, settling_time


def main():
    print("=" * 70)
    print("PID CONTROL DEMO: JOINT ANGLE SETPOINT TRACKING UNDER DISTURBANCE")
    print("=" * 70)
    setpoint = 90.0
    print(f"Target angle: {setpoint} deg | Plant: inertia=1.0, damping=0.8, "
          f"constant disturbance torque=1.5 (models gravity)\n")

    configs = {
        "P-only          (Kp=4.0, Ki=0.0,  Kd=0.0)": (4.0, 0.0, 0.0),
        "PI              (Kp=4.0, Ki=1.0,  Kd=0.0)": (4.0, 1.0, 0.0),
        "PID well-tuned  (Kp=6.0, Ki=3.0,  Kd=2.5)": (6.0, 3.0, 2.5),
        "PID aggressive  (Kp=25.0, Ki=3.0, Kd=0.2)": (25.0, 3.0, 0.2),
    }

    results = {}
    for label, (kp, ki, kd) in configs.items():
        times, angles, commands = run_simulation(kp, ki, kd, setpoint=setpoint)
        sse, overshoot, settle_t = analyze_response(times, angles, setpoint)
        results[label] = (times, angles, commands)
        settle_str = f"{settle_t:.2f}s" if settle_t is not None else "n/a"
        print(f"{label}")
        print(f"    final angle={angles[-1]:7.3f} deg  "
              f"steady-state error={sse:7.3f} deg  "
              f"overshoot={overshoot:6.2f}%  settling~={settle_str}")

    # -----------------------------------------------------------------
    # Sanity checks matching the theory's claims
    # -----------------------------------------------------------------
    p_only_sse = abs(setpoint - results["P-only          (Kp=4.0, Ki=0.0,  Kd=0.0)"][1][-1])
    pi_sse = abs(setpoint - results["PI              (Kp=4.0, Ki=1.0,  Kd=0.0)"][1][-1])
    print()
    assert p_only_sse > 0.2, "P-only should leave a visible steady-state error under disturbance"
    print(f"Check: P-only leaves steady-state error ({p_only_sse:.3f} deg) -- matches theory.")
    assert pi_sse < 0.1, "Adding an I term should drive steady-state error near zero"
    print(f"Check: PI eliminates steady-state error ({pi_sse:.4f} deg) -- matches theory.")

    good_angles = results["PID well-tuned  (Kp=6.0, Ki=3.0,  Kd=2.5)"][1]
    agg_angles = results["PID aggressive  (Kp=25.0, Ki=3.0, Kd=0.2)"][1]
    good_overshoot = (good_angles.max() - setpoint) / setpoint * 100
    agg_overshoot = (agg_angles.max() - setpoint) / setpoint * 100
    assert agg_overshoot > good_overshoot, \
        "Aggressive high-Kp/low-Kd tuning should overshoot more than well-damped tuning"
    print(f"Check: aggressive tuning overshoots more ({agg_overshoot:.1f}%) than "
          f"well-tuned PID ({good_overshoot:.1f}%) -- matches theory.")

    # -----------------------------------------------------------------
    # Plot step responses
    # -----------------------------------------------------------------
    fig, axes = plt.subplots(1, 2, figsize=(14, 5.5))
    fig.suptitle("PID Control: Step Response Comparison Under Constant Disturbance")

    colors = {
        "P-only          (Kp=4.0, Ki=0.0,  Kd=0.0)": "tab:red",
        "PI              (Kp=4.0, Ki=1.0,  Kd=0.0)": "tab:orange",
        "PID well-tuned  (Kp=6.0, Ki=3.0,  Kd=2.5)": "tab:green",
        "PID aggressive  (Kp=25.0, Ki=3.0, Kd=0.2)": "tab:purple",
    }

    ax = axes[0]
    for label, (times, angles, commands) in results.items():
        ax.plot(times, angles, label=label.split("(")[0].strip(), color=colors[label])
    ax.axhline(setpoint, color="black", linestyle="--", linewidth=1, label="setpoint")
    ax.set_xlabel("time (s)")
    ax.set_ylabel("joint angle (deg)")
    ax.set_title("Angle response")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)

    ax = axes[1]
    for label, (times, angles, commands) in results.items():
        ax.plot(times, commands, label=label.split("(")[0].strip(), color=colors[label])
    ax.set_xlabel("time (s)")
    ax.set_ylabel("controller output (torque command)")
    ax.set_title("Control effort")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3)

    plt.tight_layout()
    out_path = "pid_control_demo.png"
    plt.savefig(out_path, dpi=120)
    print(f"\nSaved plot to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()

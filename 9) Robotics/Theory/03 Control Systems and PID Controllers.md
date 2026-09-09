# The Control Loop

--> A **control loop** is the mechanism that keeps a system doing what you want despite disturbances and imperfect actuators. Every control loop has the same anatomy:

--> **Setpoint** -- the desired value (e.g. "hold the arm at 45 degrees", "keep forward speed at 1.0 m/s").
--> **Plant** -- the physical system being controlled (the motor + joint + load).
--> **Sensor** -- measures the plant's actual current value (an encoder reading the real joint angle).
--> **Error** -- setpoint minus actual measured value.
--> **Controller** -- takes the error and computes a command to send to the actuator, trying to drive error toward zero.
--> **Feedback** -- the loop of continuously re-measuring the plant's response to the last command and feeding that back into the next error calculation.

# Open-Loop vs Closed-Loop Control

--> **Open-loop control** sends a command without ever checking whether it worked -- "run the motor at this voltage for this long." It's simple and requires no sensor, but it's blind to disturbances: a heavier load, friction, or a bump will change the actual outcome and the controller has no way to notice or correct.

--> **Closed-loop control** continuously measures the actual result and adjusts the command based on the error between desired and actual. It costs a sensor and some computation, but it can correct for disturbances that open-loop control simply cannot see. Essentially all serious robotics control is closed-loop -- open-loop is only acceptable for very simple, low-stakes, or well-characterized systems.

# PID Controllers

--> **PID (Proportional-Integral-Derivative)** is the workhorse closed-loop controller of essentially all of classical robotics and industrial control. It computes its output command as the sum of three terms, each responding to a different aspect of the error signal over time.

--> **P (Proportional)** -- output proportional to the *current* error: `Kp * error`. Bigger error, bigger correction, right now. On its own, P leaves a persistent **steady-state error** for systems with constant disturbances (e.g. gravity pulling on an arm) -- it settles where the correction exactly balances the disturbance, which is not exactly at the setpoint.

--> **I (Integral)** -- output proportional to the *accumulated* error over time: `Ki * sum(error)`. This is what eliminates that steady-state error -- as long as any error persists, the integral term keeps growing and pushing harder until the error is driven to zero. The failure mode is **integral windup**: if the actuator is saturated (already at max output) for a long time, the accumulated integral term grows huge and causes a large overshoot once the system finally starts responding -- real implementations clamp or reset the integral term to guard against this.

--> **D (Derivative)** -- output proportional to the *rate of change* of error: `Kd * d(error)/dt`. This is the damping term -- it reacts to how fast the error is closing and pushes back against fast changes, which suppresses overshoot and oscillation. It's also the most noise-sensitive term, since it's essentially differentiating a noisy signal (see the previous chapter on sensor noise) -- naive implementations often apply a small low-pass filter to the derivative term specifically.

# Python PID Implementation

```python
import time

class PIDController:
    def __init__(self, kp, ki, kd, setpoint):
        self.kp, self.ki, self.kd = kp, ki, kd
        self.setpoint = setpoint
        self.integral = 0.0
        self.prev_error = 0.0
        self.prev_time = time.monotonic()

    def update(self, measured_value):
        now = time.monotonic()
        dt = now - self.prev_time
        if dt <= 0:
            dt = 1e-6  # guard against divide-by-zero on very fast loops

        error = self.setpoint - measured_value
        self.integral += error * dt
        derivative = (error - self.prev_error) / dt

        output = (self.kp * error) + (self.ki * self.integral) + (self.kd * derivative)

        self.prev_error = error
        self.prev_time = now
        return output

# Example: holding a joint at 90 degrees
pid = PIDController(kp=1.2, ki=0.1, kd=0.05, setpoint=90.0)
current_angle = 60.0  # from an encoder reading

for _ in range(5):
    motor_command = pid.update(current_angle)
    # In a real system, motor_command drives the actuator; here we fake the plant's response
    current_angle += motor_command * 0.1
    time.sleep(0.02)
    print(f"angle={current_angle:.2f}  command={motor_command:.2f}")
```

--> Note the loop structure: read sensor -> compute error -> compute PID output -> send to actuator -> wait -> repeat. This is the sense-think-act loop from the roadmap, made completely concrete.

# Tuning Trade-offs

--> Tuning Kp, Ki, Kd is fundamentally a trade-off between responsiveness and stability, and there's no universally correct setting -- it depends on the plant's physical characteristics (mass, friction, actuator strength).

--> **Aggressive tuning** (high Kp, relatively low Kd) reaches the setpoint quickly but tends to overshoot and oscillate around it before settling, sometimes never settling cleanly if pushed too far (marginal or outright instability).

--> **Conservative/stable tuning** (lower Kp, higher Kd relative to Kp) approaches the setpoint smoothly with little or no overshoot, but responds sluggishly to disturbances or setpoint changes.

--> A common manual tuning heuristic: raise Kp until the system oscillates persistently, back it off slightly, then add Kd to damp remaining oscillation, then add just enough Ki to remove any remaining steady-state error without introducing windup-driven overshoot. Automated methods (Ziegler-Nichols and its variants) formalize this same process.

# Deep Dive: PID Doesn't Know Physics, and That's Both the Point and the Limit

--> A PID controller doesn't model the plant at all -- it never "knows" that the plant is an arm with a specific mass and a gravity torque acting on it, or a motor with a specific torque curve. It only reacts to the error signal. This is precisely why it's so widely used: it's simple, robust to unmodeled effects, and works reasonably well across an enormous range of physical systems with only three numbers to tune.

--> It's also exactly where PID breaks down: for highly nonlinear systems (a legged robot's dynamics change completely depending on which feet are on the ground) or systems where you can predict the disturbance in advance (gravity's effect on an arm is fully computable from its current joint angles), a plain PID loop is fighting the same predictable disturbance over and over, every single loop iteration, instead of just cancelling it out directly. This is why more advanced robots layer a **feedforward** term (a direct physics-based prediction, e.g. computed gravity compensation) on top of PID's purely reactive feedback -- and why the most advanced systems replace hand-tuned control laws entirely with learned control policies from the Machine Learning folder.

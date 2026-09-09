"""
Dodge the Falling Blocks
=========================
A small, real, playable pygame game written to demonstrate three chapters
from the Theory notes in actual, working code:

    1) Fundamentals/Game Loop        -> see section "GAME LOOP" below
    2) Physics/Collision Detection   -> see section "AABB COLLISION" below
    3) Architecture (Object Pooling) -> see section "OBJECT POOL" below

HOW TO PLAY
-----------
    - Move the blue player square with LEFT/RIGHT arrow keys (or A/D).
    - Red blocks fall from the top of the screen. Avoid them.
    - You have 3 lives (shown top-left). Losing all 3 ends the game.
    - Score increases every second you survive.
    - Press ESC or close the window to quit. Press R to restart after game over.

RUN
---
    pip install pygame
    python game.py
"""

import random
import sys
import pygame

# ---------------------------------------------------------------------------
# CONFIG / CONSTANTS
# ---------------------------------------------------------------------------
SCREEN_WIDTH = 480
SCREEN_HEIGHT = 640
FPS = 60

PLAYER_WIDTH, PLAYER_HEIGHT = 50, 20
PLAYER_SPEED = 320.0          # pixels per second (frame-rate independent)
PLAYER_COLOR = (60, 140, 255)

OBSTACLE_WIDTH, OBSTACLE_HEIGHT = 40, 40
OBSTACLE_COLOR = (220, 60, 60)
OBSTACLE_POOL_SIZE = 12       # max simultaneous obstacles ever needed
OBSTACLE_MIN_SPEED = 150.0
OBSTACLE_MAX_SPEED = 380.0
OBSTACLE_SPAWN_INTERVAL = 0.65  # seconds between spawns (decreases with score)

BG_COLOR = (18, 18, 24)
TEXT_COLOR = (235, 235, 235)
STARTING_LIVES = 3


# ---------------------------------------------------------------------------
# OBJECT POOL  (Theory: Architecture -> Object Pooling)
# ---------------------------------------------------------------------------
# Instead of doing `Obstacle()` (allocate) every time a block spawns and
# letting it be garbage-collected when it falls off-screen (destroy), we
# pre-allocate a fixed number of Obstacle objects ONCE at startup and just
# flip an `active` flag on/off. This avoids repeated allocation/GC churn,
# which is exactly the problem object pooling solves for frequently
# spawned/destroyed objects like bullets, particles, or falling obstacles.
class Obstacle:
    """A single reusable falling-block object. Lives in the pool forever;
    only its position/speed/active-state change between uses."""

    def __init__(self):
        self.active = False
        self.rect = pygame.Rect(0, 0, OBSTACLE_WIDTH, OBSTACLE_HEIGHT)
        self.speed = 0.0  # pixels per second, set on spawn/reset

    def spawn(self, x, speed):
        """Reset (reuse) this pooled obstacle instead of creating a new one."""
        self.rect.x = x
        self.rect.y = -OBSTACLE_HEIGHT
        self.speed = speed
        self.active = True

    def deactivate(self):
        """Return this obstacle to the pool (mark inactive) instead of
        deleting it. It will be reused by the next spawn() call."""
        self.active = False

    def update(self, dt):
        # GAME LOOP concept: movement is `speed * dt`, not a fixed pixel
        # step, so obstacles fall at the same real-world speed regardless
        # of frame rate.
        self.rect.y += self.speed * dt
        if self.rect.top > SCREEN_HEIGHT:
            self.deactivate()

    def draw(self, surface):
        pygame.draw.rect(surface, OBSTACLE_COLOR, self.rect)


class ObstaclePool:
    """Fixed-size pool of Obstacle objects.

    `get_free()` hands back an already-allocated-but-inactive Obstacle to
    reuse. If every obstacle in the pool is currently active (on screen),
    it simply returns None and the spawner skips that spawn tick -- we
    never allocate beyond the pool size.
    """

    def __init__(self, size):
        # All objects allocated up-front, exactly once.
        self._pool = [Obstacle() for _ in range(size)]

    def get_free(self):
        for obstacle in self._pool:
            if not obstacle.active:
                return obstacle
        return None  # pool exhausted -- caller should just wait

    def active_obstacles(self):
        return (o for o in self._pool if o.active)

    def update(self, dt):
        for obstacle in self._pool:
            if obstacle.active:
                obstacle.update(dt)

    def draw(self, surface):
        for obstacle in self._pool:
            if obstacle.active:
                obstacle.draw(surface)


# ---------------------------------------------------------------------------
# AABB COLLISION DETECTION  (Theory: Physics/Collision Detection)
# ---------------------------------------------------------------------------
def aabb_collision(rect_a: pygame.Rect, rect_b: pygame.Rect) -> bool:
    """Axis-Aligned Bounding Box collision test, written out explicitly
    (rather than relying purely on pygame.Rect.colliderect) so the AABB
    overlap logic from the Physics/Collision Detection chapter is visible:

    Two axis-aligned rectangles overlap only if they overlap on BOTH the
    X axis AND the Y axis simultaneously. If they are separated on either
    axis, there is no collision.
    """
    x_overlap = rect_a.left < rect_b.right and rect_a.right > rect_b.left
    y_overlap = rect_a.top < rect_b.bottom and rect_a.bottom > rect_b.top
    return x_overlap and y_overlap


# ---------------------------------------------------------------------------
# PLAYER
# ---------------------------------------------------------------------------
class Player:
    def __init__(self):
        self.rect = pygame.Rect(
            (SCREEN_WIDTH - PLAYER_WIDTH) // 2,
            SCREEN_HEIGHT - 60,
            PLAYER_WIDTH,
            PLAYER_HEIGHT,
        )

    def update(self, dt, keys):
        # GAME LOOP concept: delta-time-based movement.
        # `dt` is the time (seconds) the previous frame took. Multiplying
        # speed (px/sec) by dt gives a frame-rate-independent displacement,
        # so the player moves at the same real-world speed on a fast PC
        # (many small steps) or a slow PC (fewer, larger steps).
        dx = 0.0
        if keys[pygame.K_LEFT] or keys[pygame.K_a]:
            dx -= PLAYER_SPEED * dt
        if keys[pygame.K_RIGHT] or keys[pygame.K_d]:
            dx += PLAYER_SPEED * dt

        self.rect.x += round(dx)
        self.rect.x = max(0, min(SCREEN_WIDTH - PLAYER_WIDTH, self.rect.x))

    def draw(self, surface):
        pygame.draw.rect(surface, PLAYER_COLOR, self.rect)


# ---------------------------------------------------------------------------
# GAME
# ---------------------------------------------------------------------------
class Game:
    def __init__(self):
        pygame.init()
        self.screen = pygame.display.set_mode((SCREEN_WIDTH, SCREEN_HEIGHT))
        pygame.display.set_caption("Dodge the Falling Blocks")
        self.clock = pygame.time.Clock()
        self.font = pygame.font.SysFont(None, 28)
        self.big_font = pygame.font.SysFont(None, 48)
        self.reset()

    def reset(self):
        self.player = Player()
        self.pool = ObstaclePool(OBSTACLE_POOL_SIZE)
        self.lives = STARTING_LIVES
        self.score = 0.0
        self.spawn_timer = 0.0
        self.game_over = False

    def spawn_obstacle(self):
        # ObjectPool.get_free() reuses an inactive obstacle if one exists.
        obstacle = self.pool.get_free()
        if obstacle is None:
            return  # pool exhausted this tick; try again next spawn interval
        x = random.randint(0, SCREEN_WIDTH - OBSTACLE_WIDTH)
        speed = random.uniform(OBSTACLE_MIN_SPEED, OBSTACLE_MAX_SPEED)
        obstacle.spawn(x, speed)

    def update(self, dt):
        if self.game_over:
            return

        keys = pygame.key.get_pressed()
        self.player.update(dt, keys)

        # Spawn timer: interval shrinks slightly as score grows, for
        # increasing difficulty. Still fully dt-driven (frame-rate independent).
        self.spawn_timer += dt
        interval = max(0.25, OBSTACLE_SPAWN_INTERVAL - self.score * 0.01)
        if self.spawn_timer >= interval:
            self.spawn_timer = 0.0
            self.spawn_obstacle()

        self.pool.update(dt)

        # --- AABB COLLISION check between player and every active obstacle ---
        for obstacle in list(self.pool.active_obstacles()):
            if aabb_collision(self.player.rect, obstacle.rect):
                obstacle.deactivate()  # returned to pool, not deleted
                self.lives -= 1
                if self.lives <= 0:
                    self.game_over = True

        self.score += dt * 10  # 10 points per second survived

    def draw(self):
        self.screen.fill(BG_COLOR)
        self.player.draw(self.screen)
        self.pool.draw(self.screen)

        hud = self.font.render(
            f"Lives: {self.lives}   Score: {int(self.score)}", True, TEXT_COLOR
        )
        self.screen.blit(hud, (10, 10))

        if self.game_over:
            msg = self.big_font.render("GAME OVER", True, (255, 90, 90))
            sub = self.font.render("Press R to restart, ESC to quit", True, TEXT_COLOR)
            self.screen.blit(
                msg, (SCREEN_WIDTH // 2 - msg.get_width() // 2, SCREEN_HEIGHT // 2 - 40)
            )
            self.screen.blit(
                sub, (SCREEN_WIDTH // 2 - sub.get_width() // 2, SCREEN_HEIGHT // 2 + 10)
            )

        pygame.display.flip()

    # -----------------------------------------------------------------
    # GAME LOOP  (Theory: Fundamentals/Game Loop)
    # -----------------------------------------------------------------
    # The canonical game loop: while running -> (1) process input/events,
    # (2) update simulation state by delta-time `dt`, (3) render, (4) cap
    # frame rate. `self.clock.tick(FPS)` both caps the frame rate AND
    # returns the milliseconds elapsed since the previous call, which we
    # convert to seconds to get `dt` -- the delta-time used everywhere
    # above for frame-rate-independent movement.
    def run(self):
        running = True
        while running:
            dt_ms = self.clock.tick(FPS)   # cap at FPS, measure elapsed time
            dt = dt_ms / 1000.0            # convert ms -> seconds

            # 1) INPUT / EVENTS
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False
                elif event.type == pygame.KEYDOWN:
                    if event.key == pygame.K_ESCAPE:
                        running = False
                    elif event.key == pygame.K_r and self.game_over:
                        self.reset()

            # 2) UPDATE (simulation advances by dt, not by a fixed step)
            self.update(dt)

            # 3) RENDER
            self.draw()

        pygame.quit()
        sys.exit()


if __name__ == "__main__":
    Game().run()

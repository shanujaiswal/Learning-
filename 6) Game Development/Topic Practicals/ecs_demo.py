"""
ECS Demo -- Entity-Component-System from scratch
=================================================
Standalone, no pygame dependency. Demonstrates the ECS pattern described
in the Theory notes (03 Game Architecture -- ECS, State Machines and
Object Pooling), isolated from game.py so the pattern is easy to see on
its own.

Concept
-------
    ENTITY    = just an ID (an int). It has no data and no behaviour of
                its own -- it is only a label used to look components up.
    COMPONENT = plain data, nothing else (no methods that "do" things).
                Here: Position, Velocity, Health. An entity "has" a
                component simply by having an entry for it in that
                component's dict, keyed by entity ID.
    SYSTEM    = a plain function that iterates over the entities which
                have the particular combination of components it cares
                about, and mutates their data. Behaviour lives here, in
                systems, NOT in the entity or the component.

This is composition over inheritance: a "Rock" entity that only has
Position (no Velocity, no Health) is automatically skipped by
movement_system and damage_system because it doesn't have the required
components -- no subclassing, no is-a relationship required anywhere.

Contrast with deep inheritance
-------------------------------
The "classic OOP" way to model this would be something like:

    class GameObject:
        def update(self, dt): ...
    class MovableObject(GameObject):
        def __init__(self):
            self.vx = self.vy = 0
        def update(self, dt):
            self.x += self.vx * dt; self.y += self.vy * dt
    class DamageableObject(MovableObject):
        def __init__(self):
            super().__init__()
            self.hp = 100
        def take_damage(self, amount):
            self.hp -= amount
    class Player(DamageableObject): ...
    class Enemy(DamageableObject): ...

This works fine until you need an entity that is Damageable but NOT
Movable (a turret), or Movable but NOT Damageable (a decorative cloud),
or needs a totally new combination (a Poisonable Movable object that
isn't otherwise Damageable). With single inheritance you either duplicate
code, invent awkward mixins, or grow one giant "God" base class that
everything inherits just to get the one method it needs. ECS avoids all
of this: capabilities are just "does this entity ID appear in this
component dict?", so any combination of components is free to construct,
and adding a new capability means adding a new component dict + system,
touching nothing else.

RUN
---
    python ecs_demo.py
"""

from dataclasses import dataclass, field
import itertools


# ---------------------------------------------------------------------------
# ENTITY
# ---------------------------------------------------------------------------
# An entity is nothing but an integer ID. No class, no fields, no methods.
_next_entity_id = itertools.count(1)


def create_entity():
    """Hand out a fresh, unique entity ID. That's the entire "entity"."""
    return next(_next_entity_id)


# ---------------------------------------------------------------------------
# COMPONENTS  (plain data only -- no behaviour)
# ---------------------------------------------------------------------------
@dataclass
class Position:
    x: float
    y: float


@dataclass
class Velocity:
    dx: float
    dy: float


@dataclass
class Health:
    current: int
    max: int = 100


# ---------------------------------------------------------------------------
# WORLD: component storage
# ---------------------------------------------------------------------------
# Each component type gets its own dict, keyed by entity ID. An entity
# "has" a component purely by virtue of being present as a key in that
# dict -- there is no entity object recording which components it owns.
class World:
    def __init__(self):
        self.positions: dict[int, Position] = {}
        self.velocities: dict[int, Velocity] = {}
        self.healths: dict[int, Health] = {}
        self.entities: list[int] = []

    def add_entity(self, entity, *components):
        self.entities.append(entity)
        for component in components:
            if isinstance(component, Position):
                self.positions[entity] = component
            elif isinstance(component, Velocity):
                self.velocities[entity] = component
            elif isinstance(component, Health):
                self.healths[entity] = component


# ---------------------------------------------------------------------------
# SYSTEMS  (plain functions -- behaviour lives here, not on entities)
# ---------------------------------------------------------------------------
def movement_system(world: World, dt: float):
    """Operates only on entities that have BOTH Position and Velocity.
    An entity with just Position (e.g. a static rock) is untouched."""
    for entity, velocity in world.velocities.items():
        position = world.positions.get(entity)
        if position is None:
            continue  # has Velocity but no Position -- nothing to move
        position.x += velocity.dx * dt
        position.y += velocity.dy * dt


def damage_over_time_system(world: World, dt: float, drain_per_sec: float = 5.0):
    """Operates only on entities that have Health. Entities without
    Health (e.g. the static rock) are simply never visited."""
    for entity, health in world.healths.items():
        health.current = max(0, health.current - drain_per_sec * dt)


def render_system(world: World, tick: int):
    """Stand-in for a real render system: prints the current state of
    every entity that has a Position, plus Health if present."""
    print(f"-- tick {tick} --")
    for entity in world.entities:
        pos = world.positions.get(entity)
        hp = world.healths.get(entity)
        parts = [f"entity {entity}"]
        if pos is not None:
            parts.append(f"pos=({pos.x:.1f}, {pos.y:.1f})")
        if hp is not None:
            parts.append(f"hp={hp.current:.1f}/{hp.max}")
        print("   " + " ".join(parts))


# ---------------------------------------------------------------------------
# DEMO
# ---------------------------------------------------------------------------
def main():
    world = World()

    # Player: Position + Velocity + Health -- moves and takes damage.
    player = create_entity()
    world.add_entity(player, Position(0.0, 0.0), Velocity(2.0, 1.0), Health(100, 100))

    # Enemy: Position + Velocity + Health -- moves toward player and has HP.
    enemy = create_entity()
    world.add_entity(enemy, Position(20.0, 0.0), Velocity(-1.0, 0.0), Health(30, 30))

    # Rock: Position ONLY -- no Velocity, no Health. Static scenery.
    # movement_system and damage_over_time_system both skip it automatically
    # because it never appears in world.velocities or world.healths.
    rock = create_entity()
    world.add_entity(rock, Position(5.0, 5.0))

    # Turret: Health ONLY, no Position/Velocity -- damageable but immobile
    # and not "in space" at all in this simplified example. Impossible to
    # express cleanly with the inheritance chain sketched in the docstring
    # above without extra mixins; trivial here.
    turret = create_entity()
    world.add_entity(turret, Health(50, 50))

    dt = 1.0  # simulate in whole-second ticks for readable output
    for tick in range(1, 6):
        movement_system(world, dt)
        damage_over_time_system(world, dt, drain_per_sec=5.0)
        render_system(world, tick)

    print("\nFinal check: rock position never changed, turret has no position:")
    print(f"   rock  -> {world.positions[rock]}")
    print(f"   turret -> position component present? {turret in world.positions}")


if __name__ == "__main__":
    main()

package orbital.entities.abilities;

import orbital.gen.*;

/**
 * A dorsal module that aims like the block it reproduces.
 *
 * <p>A block turret turns toward its target and its shot leaves the block's own muzzle. A
 * dorsal module has to do the same thing from its anchor on the hull, which is why these
 * modules report a rotating aim instead of firing from the carrier's centre and never moving.</p>
 *
 * <p>Implemented by the support modules whose block has a barrel: the liquid jets
 * ({@code wave}, {@code tsunami}), the tractor beam ({@code parallax}) and the repair beams
 * ({@code repair-point}, {@@code repair-turret}). Area modules - projectors, repair fields,
 * force fields - have no muzzle to turn, and do not implement this.</p>
 */
public interface AimingModule{
    /** Sets the module's hull-local dock, in world units. Called once by {@code SupportMount}. */
    void anchor(float anchorX, float anchorY);

    /** @return the current aim, relative to the hull, in degrees. */
    float rotation();

    /** @return the module's dock in world coordinates, already rotated into the hull. */
    float moduleX(Unit unit);

    /** @return the module's dock in world coordinates, already rotated into the hull. */
    float moduleY(Unit unit);
}

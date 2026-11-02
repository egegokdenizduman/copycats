package com.copycatsplus.copycats.foundation.copycat;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Stores additional context when rendering copycats.
 */
@ApiStatus.Internal
public class CopycatExternalContext {
    /**
     * Stores the currently rendering property for multi-state copycats. The stored value is thread-local.
     * <p>
     * This is used when determining block colors
     */
    private static final ThreadLocal<String> propertyForBlockColor = new ThreadLocal<>();
    /**
     * Stores the currently rendering property for multi-state copycats. The stored value is thread-local.
     * <p>
     * This is used when block shape calculation results in a valid rendering property for getAppearance
     */
    private static final ThreadLocal<String> propertyForAppearance = new ThreadLocal<>();
    /**
     * Used to determine whether a {@link ICopycatBlock#getAppearance} call is made for determining blocking logic,
     * which causes the method to return appearances differently.
     */
    private static final ThreadLocal<Boolean> forBlockingLogic = ThreadLocal.withInitial(() -> false);

    /**
     * Used while building the second connected texture result of a copycat, in which flush faces also connect along
     * edges that only partly overlap. Holds the faces that use that result, null otherwise.
     */
    private static final ThreadLocal<Set<Direction>> partialEdgeFaces = new ThreadLocal<>();

    public static void setPartialEdgeFaces(@Nullable Set<Direction> faces) {
        partialEdgeFaces.set(faces);
    }

    public static boolean shouldConnectPartialEdges() {
        return partialEdgeFaces.get() != null;
    }

    /**
     * Whether the second connected texture result can leave out this face, because no partial edge uses it.
     */
    public static boolean skipsPartialEdgeFace(Direction face) {
        Set<Direction> faces = partialEdgeFaces.get();
        return faces != null && !faces.contains(face);
    }

    public static void setPropertyForBlockColor(String property) {
        propertyForBlockColor.set(property);
    }

    public static String getPropertyForBlockColor() {
        return propertyForBlockColor.get();
    }

    public static void setPropertyForAppearance(String property) {
        propertyForAppearance.set(property);
    }

    public static String getPropertyForAppearance() {
        return propertyForAppearance.get();
    }

    public static void setForBlockingLogic(boolean value) {
        forBlockingLogic.set(value);
    }

    public static boolean isForBlockingLogic() {
        return forBlockingLogic.get();
    }
}

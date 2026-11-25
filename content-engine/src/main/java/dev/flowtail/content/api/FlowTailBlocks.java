package dev.flowtail.content.api;

import java.util.Optional;
import org.bukkit.block.Block;

/**
 * Read access to FlowTail blocks in the world. Obtain it from {@code ContentRuntime.blocks()} after the content
 * plugin has enabled. Main thread only.
 */
public interface FlowTailBlocks {
    /**
     * The content id (for example {@code example:rock}) of the FlowTail block at this position, including ids whose
     * plugin is no longer installed; empty for every ordinary block.
     */
    Optional<String> idAt(Block block);

    /** True when {@link #idAt} is present. */
    default boolean isFlowTailBlock(Block block) { return idAt(block).isPresent(); }

}

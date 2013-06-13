package org.apache.karaf.tooling.semantic.transformer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.karaf.tooling.semantic.range.VersionType;
import org.apache.mina.util.IdentityHashSet;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.DependencyGraphTransformationContext;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.graph.DependencyVisitor;
import org.sonatype.aether.util.artifact.ArtifacIdUtils;

import org.apache.karaf.tooling.semantic.eclipse.ConflictResolver;

/**
 * Customer transform.
 * <p>
 * Supported features
 * <li>remove all range snapshots
 * <li>remove all but regex-match range snapshots
 */
public class CustomTransformer extends BaseTransformer {

	/**
	 * Enable all snapshot dependencies which come form the range.
	 */
	public static final String ENABLE_RANGE_SNAPSHOT = "enableRangeSnapshot";

	/**
	 * Enable only matching snapshot dependencies which come form the range.
	 * <p>
	 * {@code <groupId>:<artifactId>:<extension>[:<classifier>]:<version>}
	 */
	public static final String ENABLE_RANGE_SNAPSHOT_REGEX = "enableRangeSnapshotRegex";

	private final boolean enableRangeSnapshot;

	private final Pattern enableRangeSnapshotRegex;

	public CustomTransformer(final Map<String, String> resolverSettings) {

		final String enableText = resolverSettings.get(ENABLE_RANGE_SNAPSHOT);
		enableRangeSnapshot = enableText == null ? false : Boolean
				.parseBoolean(enableText);

		final String regexText = resolverSettings
				.get(ENABLE_RANGE_SNAPSHOT_REGEX);
		enableRangeSnapshotRegex = regexText == null ? null : Pattern
				.compile(regexText);

	}

	@Override
	public DependencyNode transformGraph(final DependencyNode node,
			final DependencyGraphTransformationContext context)
			throws RepositoryException {

		if (!this.enableRangeSnapshot) {
			removeRangeSnapshot(node, context);
		}

		return node;
	}

	/**
	 * Remove range-bound snapshots from collected resolver structures.
	 * <p>
	 * Does not affect non-range snapshots.
	 */
	protected void removeRangeSnapshot(final DependencyNode root,
			final DependencyGraphTransformationContext context) {

		final Map<DependencyNode, Object> conflictMap = conflictMap(context);

		final Set<Entry<DependencyNode, Object>> entrySet = conflictMap
				.entrySet();

		final Map<DependencyNode, Object> removalMap = new HashMap<DependencyNode, Object>();

		for (final Entry<DependencyNode, Object> entry : entrySet) {

			final Object key = entry.getValue();
			final DependencyNode node = entry.getKey();
			final Artifact artifact = node.getDependency().getArtifact();
			final VersionType type = VersionType.form(node
					.getVersionConstraint());
			final boolean isSnapshot = artifact.isSnapshot();

			if (!isSnapshot) {
				continue;
			}

			if (type != VersionType.RANGE) {
				continue;
			}

			if (enableRangeSnapshotRegex == null) {
				/** Remove all snapshots. */
				removalMap.put(node, key);
			} else {
				/** Remove non-matching snapshots. */
				final String artifactGUID = ArtifacIdUtils.toId(artifact);
				if (enableRangeSnapshotRegex.matcher(artifactGUID).matches()) {
					/** Keep. */
				} else {
					removalMap.put(node, key);
				}
			}

		}

		final Set<DependencyNode> removalSet = new HashSet<DependencyNode>();

		for (final Entry<DependencyNode, Object> remove : removalMap.entrySet()) {
			// remove(root, remove, removalSet);
			// log("remove=" + remove.getKey() + " @ " + remove.getValue());
		}

		final DependencyVisitor visitor = new DependencyVisitor() {

			@Override
			public boolean visitEnter(DependencyNode node) {
				log("enter=" + node);
				return true;
			}

			@Override
			public boolean visitLeave(DependencyNode node) {
				log("leave=" + node);
				return true;
			}

		};
		// root.accept(visitor);

		// scan(root, 0);

		log("cycles=" + cycles(context));

		/** pom.xml -> dependencies */
		final Map<Object, List<DependencyNode>> pomMap = //
		new IdentityHashMap<Object, List<DependencyNode>>();

		discover(root, pomMap);

	}

	/** Identity of original pom.xml */
	static class Project {

		/**
		 * Aether uses children list object as a proxy-id to the original
		 * pom.xml.
		 * <p>
		 * {@link ConflictResolver.ConflictItem}
		 * "unique owner of a child node which is the child list"
		 */
		static Object key(DependencyNode node) {
			return node.getChildren();
		}

		/** Unique id of the original pom.xml */
		final Object key;

		final List<DependencyNode> nodeList;

		Project(List<DependencyNode> nodeList) {
			this.key = nodeList;
			this.nodeList = nodeList;
		}

		/** Nodes which represent the same pom.xml */
		final Set<DependencyNode> pomSet = new IdentityHashSet<DependencyNode>();

	}

	protected void discover(final DependencyNode root,
			final Map<Object, List<DependencyNode>> pomMap) {

		final List<DependencyNode> nodeList = root.getChildren();

		final Object parent = nodeList;

		if (pomMap.containsKey(parent)) {
			/** Already discovered. */
			return;
		} else {
			/** New - remember now. */
			pomMap.put(parent, nodeList);
		}

		for (final DependencyNode node : nodeList) {
			discover(node, pomMap);
		}

	}

	/** XXX */
	protected void remove(final Map<Object, List<DependencyNode>> pomMap,
			Map<DependencyNode, Object> removalMap) {

	}

	protected void scan(DependencyNode root, int level) {
		log("level=" + level + " " + root + " " + root.getChildren().hashCode());
		for (final DependencyNode node : root.getChildren()) {
			scan(node, level + 1);
		}
	}

	protected void remove(final DependencyNode root,
			final DependencyNode remove, final Set<DependencyNode> removalSet) {

		for (final DependencyNode node : root.getChildren()) {
			if (node == remove) {
				removalSet.add(root);
			} else {
				remove(node, remove, removalSet);
			}
		}

	}

	/**
	 * Do not use.
	 */
	protected void removeRangeSnapshot(final DependencyNode root,
			final DependencyGraphTransformationContext context, final int level) {

		final Iterator<DependencyNode> iterator = root.getChildren().iterator();

		while (iterator.hasNext()) {

			final DependencyNode node = iterator.next();

			final VersionType versionType = VersionType.form(node
					.getVersionConstraint());

			final Artifact artifact = node.getDependency().getArtifact();

			log("level=" + level + " artifact=" + artifact);

			switch (versionType) {
			default:

				/** Non-range snapshots are not affected. */
				break;

			case RANGE:

				/** Process only range snapshots. */
				final boolean isSnapshot = artifact.isSnapshot();
				if (enableRangeSnapshotRegex == null) {
					/** Remove all snapshots. */
					if (isSnapshot) {
						iterator.remove();
					}
				} else {
					/** Remove non-matching snapshots. */
					if (isSnapshot) {
						final String artifactGUID = ArtifacIdUtils
								.toId(artifact);
						if (enableRangeSnapshotRegex.matcher(artifactGUID)
								.matches()) {
							/** Keep. */
						} else {
							iterator.remove();
						}
					}
				}
				break;

			}

			removeRangeSnapshot(node, context, level + 1);

		}
	}

}
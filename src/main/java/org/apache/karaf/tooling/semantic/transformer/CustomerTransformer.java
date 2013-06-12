package org.apache.karaf.tooling.semantic.transformer;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.karaf.tooling.semantic.range.VersionType;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.DependencyGraphTransformationContext;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.util.artifact.ArtifacIdUtils;

/**
 * Customer transform.
 * <p>
 * Supported features
 * <li>remove all range snapshots
 * <li>remove all but regex-match range snapshots
 */
public class CustomerTransformer extends BaseTransformer {

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

	public CustomerTransformer(Map<String, String> resolverSettings) {

		String enableText = resolverSettings.get(ENABLE_RANGE_SNAPSHOT);
		enableRangeSnapshot = enableText == null ? false : Boolean
				.parseBoolean(enableText);

		String regexText = resolverSettings.get(ENABLE_RANGE_SNAPSHOT_REGEX);
		enableRangeSnapshotRegex = regexText == null ? null : Pattern
				.compile(regexText);

	}

	public DependencyNode transformGraph(DependencyNode node,
			DependencyGraphTransformationContext context)
			throws RepositoryException {

		if (!this.enableRangeSnapshot) {
			removeRangeSnapshot(node, context);
		}

		return node;
	}

	protected void removeRangeSnapshot(DependencyNode root,
			DependencyGraphTransformationContext context) {

		final Iterator<DependencyNode> iterator = root.getChildren().iterator();

		while (iterator.hasNext()) {

			final DependencyNode node = (DependencyNode) iterator.next();

			final VersionType versionType = VersionType.form(node
					.getVersionConstraint());

			final Artifact artifact = node.getDependency().getArtifact();
			final String version = artifact.getVersion();
			final String artifactGUID = ArtifacIdUtils.toId(artifact);

			// System.err.println("### artifact=" + artifact);

			switch (versionType) {
			case RANGE:
				final boolean isSnapshot = isSnapshot(version);
				if (enableRangeSnapshotRegex == null) {
					/** Remove all snapshots. */
					if (isSnapshot) {
						iterator.remove();
					}
				} else {
					/** Remove non-matching snapshots. */
					if (isSnapshot) {
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

			removeRangeSnapshot(node, context);
		}
	}

	static boolean isSnapshot(String version) {
		return version.endsWith("SNAPSHOT");
	}

}
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.tooling.ap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.karaf.tooling.ap.xform.NearestTransformer;
import org.apache.karaf.tooling.ap.xform.SemanticTransformer;
import org.apache.karaf.tooling.features.GenerateDescriptorMojo;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.collection.DependencySelector;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.impl.internal.DefaultRepositorySystem;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;
import org.sonatype.aether.util.graph.selector.AndDependencySelector;
import org.sonatype.aether.util.graph.selector.ExclusionDependencySelector;
import org.sonatype.aether.util.graph.selector.OptionalDependencySelector;
import org.sonatype.aether.util.graph.selector.ScopeDependencySelector;
import org.sonatype.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.sonatype.aether.util.graph.transformer.ConflictIdSorter;
import org.sonatype.aether.util.graph.transformer.ConflictMarker;
import org.sonatype.aether.util.graph.transformer.NearestVersionConflictResolver;
import org.sonatype.aether.util.graph.transformer.NoopDependencyGraphTransformer;

/**
 * Generates the semantic features XML file for packaging=pom
 * 
 * @goal features-generate-semantic
 * @phase compile
 * @requiresDependencyResolution runtime
 * @inheritByDefault true
 * @description TODO
 * 
 * @author Andrei Pozolotin
 */
public class GenerateSemanticMojo extends GenerateDescriptorMojo {

	/**
	 * Dependency scope to include. Default include: compile.
	 * 
	 * @parameter
	 */
	protected Set<String> scopeIncluded;
	{
		scopeIncluded = new HashSet<String>();
		scopeIncluded.add("compile");
	}

	/**
	 * Dependency scope to exclude. Default exclude: runtime, provided, system,
	 * test.
	 * 
	 * @parameter
	 */
	protected Set<String> scopeExcluded;
	{
		scopeExcluded = new HashSet<String>();
		scopeExcluded.add("runtime");
		scopeExcluded.add("provided");
		scopeExcluded.add("system");
		scopeExcluded.add("test");
	}

	/**
	 */
	public static Map<Artifact, String> prepare(MojoContext context)
			throws Exception {

		final Artifact artifact = RepositoryUtils.toArtifact(context.project
				.getArtifact());

		final Dependency dependencyRoot = new Dependency(artifact, "compile");

		final CollectRequest collectRequest = new CollectRequest(
				dependencyRoot, context.projectRepos);

		final DependencySelector selector = new AndDependencySelector(//
				new OptionalDependencySelector(), //
				new ScopeDependencySelector(context.scopeIncluded,
						context.scopeExcluded), //
				new ExclusionDependencySelector());

		final DefaultRepositorySystemSession localSession = new DefaultRepositorySystemSession(
				context.session);

		localSession.setDependencySelector(selector);

		ConflictMarker marker = new ConflictMarker();
		ConflictIdSorter sorter = new ConflictIdSorter();
		NearestTransformer nearest = new NearestTransformer();
		ChainedDependencyGraphTransformer transformer = new ChainedDependencyGraphTransformer(
				marker, sorter, nearest);

		localSession.setDependencyGraphTransformer(transformer);

		final CollectResult collectResult = context.system.collectDependencies(
				localSession, collectRequest);

		final DependencyNode collectNode = collectResult.getRoot();

		final DependencyRequest dependencyRequest = new DependencyRequest(
				collectNode, null);

		final DependencyResult resolveResult = context.system
				.resolveDependencies(localSession, dependencyRequest);

		final DependencyNode resolvedNode = resolveResult.getRoot();

		final PreorderNodeListGenerator generator = new PreorderNodeListGenerator();

		resolvedNode.accept(generator);

		final List<Dependency> dependencyList = generator.getDependencies(true);

		// [dependency:scope]
		final Map<Artifact, String> dependencyMap = new HashMap<Artifact, String>();

		for (Dependency dependency : dependencyList) {

			context.logger.info("### dependency : " + dependency);

			dependencyMap.put(dependency.getArtifact(), dependency.getScope());

		}

		return dependencyMap;

	}

	@Override
	protected void prepare() throws Exception {

		MojoContext context = new MojoContext(getLogger(), project,
				scopeIncluded, scopeExcluded, repoSystem, repoSession,
				projectRepos);

		this.localDependencies = prepare(context);

		this.treeListing = "not available";

	}

}

package org.apache.karaf.tooling.ap;

import java.util.List;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;

public class MojoContext {

	public final Logger logger;
	public final MavenProject project;
	public final Set<String> scopeIncluded;
	public final Set<String> scopeExcluded;
	public final RepositorySystem system;
	public final RepositorySystemSession session;
	public final List<RemoteRepository> projectRepos;

	public MojoContext(//
			Logger logger, //
			MavenProject project, //
			Set<String> scopeIncluded, //
			Set<String> scopeExcluded, //
			RepositorySystem system, //
			RepositorySystemSession session, //
			List<RemoteRepository> projectRepos //
	) {

		this.logger = logger;
		this.project = project;
		this.scopeIncluded = scopeIncluded;
		this.scopeExcluded = scopeExcluded;
		this.system = system;
		this.session = session;
		this.projectRepos = projectRepos;

	}

}

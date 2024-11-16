package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.catalog.DelegatingProjectDependency;
import org.gradle.api.internal.catalog.TypeSafeProjectDependencyFactory;
import javax.inject.Inject;

@NonNullApi
public class RootProjectAccessor extends TypeSafeProjectDependencyFactory {


    @Inject
    public RootProjectAccessor(DefaultProjectDependencyFactory factory, ProjectFinder finder) {
        super(factory, finder);
    }

    /**
     * Creates a project dependency on the project at path ":"
     */
    public FileSyncProjectDependency getFileSync() { return new FileSyncProjectDependency(getFactory(), create(":")); }

    /**
     * Creates a project dependency on the project at path ":file-sync-client"
     */
    public FileSyncClientProjectDependency getFileSyncClient() { return new FileSyncClientProjectDependency(getFactory(), create(":file-sync-client")); }

    /**
     * Creates a project dependency on the project at path ":file-sync-server"
     */
    public FileSyncServerProjectDependency getFileSyncServer() { return new FileSyncServerProjectDependency(getFactory(), create(":file-sync-server")); }

    /**
     * Creates a project dependency on the project at path ":file-sync-shared"
     */
    public FileSyncSharedProjectDependency getFileSyncShared() { return new FileSyncSharedProjectDependency(getFactory(), create(":file-sync-shared")); }

}

/* 
 * Sourcerer: an infrastructure for large-scale source code analysis.
 * Copyright (C) by contributors. See CONTRIBUTORS.txt for full list.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package edu.uci.ics.sourcerer.tools.java.db.importer;

import static edu.uci.ics.sourcerer.util.io.logging.Logging.logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;

import edu.uci.ics.sourcerer.tools.java.db.importer.resolver.JavaLibraryTypeModel;
import edu.uci.ics.sourcerer.tools.java.db.importer.resolver.UnknownEntityCache;
import edu.uci.ics.sourcerer.tools.java.repo.model.JavaRepositoryFactory;
import edu.uci.ics.sourcerer.tools.java.repo.model.extracted.ExtractedJarFile;
import edu.uci.ics.sourcerer.tools.java.repo.model.extracted.ExtractedJavaProject;
import edu.uci.ics.sourcerer.tools.java.repo.model.extracted.ExtractedJavaRepository;
import edu.uci.ics.sourcerer.util.Nullerator;
import edu.uci.ics.sourcerer.util.io.arguments.Argument;
import edu.uci.ics.sourcerer.util.io.arguments.BooleanArgument;
import edu.uci.ics.sourcerer.util.io.arguments.IntegerArgument;
import edu.uci.ics.sourcerer.util.io.logging.TaskProgressLogger;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public final class ParallelDatabaseImporter {
  public static Argument<Boolean> STRUCTURAL_ONLY = new BooleanArgument("structural-only", false, "Only import entities and structural relations");
  public static Argument<Integer> THREAD_COUNT = new IntegerArgument("thread-count", 4, "Number of simultaneous threads");
  
  private ParallelDatabaseImporter() {}
  
  public static void importJavaLibraries() {
    TaskProgressLogger task = TaskProgressLogger.get();
    task.start("Importing Java libraries");
    
    ExtractedJavaRepository repo = JavaRepositoryFactory.INSTANCE.loadExtractedJavaRepository(JavaRepositoryFactory.INPUT_REPO);
    
    if (repo == null) {
      task.finish();
      return;
    }
    
    importJavaLibraries(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "entity import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new JavaLibraryEntitiesImporter(nullerator);
      }
    });
    
    
    final UnknownEntityCache unknowns = UnknownEntityCache.makeUnknownEntityCache(task);
    
    importJavaLibraries(repo, new ImporterFactory<ExtractedJarFile>() {
      JavaLibraryTypeModel javaModel = JavaLibraryTypeModel.createJavaLibraryTypeModel();
      
      @Override
      public String message() {
        return "structural relation";
      }
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new JavaLibraryStructuralRelationsImporter(nullerator, javaModel, unknowns);
      }
    });
    
    if (STRUCTURAL_ONLY.getValue()) {
      task.report("Skipping referential relation import");
    } else {
      importJavaLibraries(repo, new ImporterFactory<ExtractedJarFile>() {
        JavaLibraryTypeModel javaModel = JavaLibraryTypeModel.createJavaLibraryTypeModel();
        
        @Override
        public String message() {
          return "structural relation";
        }
        @Override
        public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
          return new JavaLibraryReferentialRelationsImporter(nullerator, javaModel, unknowns);
        }
      });
    }
    
    task.finish();
  }
  
  public static void importJarFiles() {
    TaskProgressLogger task = TaskProgressLogger.get();
    task.start("Importing jar files");
    
    ExtractedJavaRepository repo = JavaRepositoryFactory.INSTANCE.loadExtractedJavaRepository(JavaRepositoryFactory.INPUT_REPO);

    if (repo == null) {
      task.finish();
      return;
    }
    
    importMavenJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "entity import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new JarEntitiesImporter(nullerator);
      }
    });
    
    importProjectJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "entity import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new JarEntitiesImporter(nullerator);
      }
    });
   

    final JavaLibraryTypeModel javaModel = JavaLibraryTypeModel.createJavaLibraryTypeModel();
    final UnknownEntityCache unknowns = UnknownEntityCache.makeUnknownEntityCache(task);
    
    importMavenJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "structural relation import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new JarStructuralRelationsImporter(nullerator, javaModel, unknowns);
      }
    });
    
    importProjectJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "structural relation import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new JarStructuralRelationsImporter(nullerator, javaModel, unknowns);
      }
    });
    
    if (STRUCTURAL_ONLY.getValue()) {
      task.report("Skipping referential relation import");
    } else {
      importMavenJars(repo, new ImporterFactory<ExtractedJarFile>() {
        @Override
        public String message() {
          return "referential relation import";
        }
        
        @Override
        public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
          return new JarReferentialRelationsImporter(nullerator, javaModel, unknowns);
        }
      });
      
      importProjectJars(repo, new ImporterFactory<ExtractedJarFile>() {
        @Override
        public String message() {
          return "referential relation import";
        }
        
        @Override
        public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
          return new JarReferentialRelationsImporter(nullerator, javaModel, unknowns);
        }
      });
    }
    
    task.finish();
  }
  
  public static void importProjects() {
    TaskProgressLogger task = TaskProgressLogger.get();
    task.start("Importing projects");
    
    ExtractedJavaRepository repo = JavaRepositoryFactory.INSTANCE.loadExtractedJavaRepository(JavaRepositoryFactory.INPUT_REPO);

    if (repo == null) {
      task.finish();
      return;
    }
    
    importProjects(repo, new ImporterFactory<ExtractedJavaProject>() {
      @Override
      public String message() {
        return "entity import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJavaProject> nullerator) {
        return new ProjectEntitiesImporter(nullerator);
      }
    });
    
    
    final JavaLibraryTypeModel javaModel = JavaLibraryTypeModel.createJavaLibraryTypeModel();
    final UnknownEntityCache unknowns = UnknownEntityCache.makeUnknownEntityCache(task);
    
    importProjects(repo, new ImporterFactory<ExtractedJavaProject>() {
      @Override
      public String message() {
        return "structural relation import";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJavaProject> nullerator) {
        return new ProjectStructuralRelationsImporter(nullerator, javaModel, unknowns);
      }
    });

    if (STRUCTURAL_ONLY.getValue()) {
      task.report("Skipping referential relation import");
    } else {
      importProjects(repo, new ImporterFactory<ExtractedJavaProject>() {
        @Override
        public String message() {
          return "referential relation import";
        }
        
        @Override
        public DatabaseImporter create(Nullerator<ExtractedJavaProject> nullerator) {
          return new ProjectReferentialRelationsImporter(nullerator, javaModel, unknowns);
        }
      });
    }
    
    task.finish();
  }
  
  public static void addBytecodeMetrics() {
    TaskProgressLogger task = TaskProgressLogger.get();
    task.start("Adding bytecode metrics");
   
    ExtractedJavaRepository repo = JavaRepositoryFactory.INSTANCE.loadExtractedJavaRepository(JavaRepositoryFactory.INPUT_REPO);

    if (repo == null) {
      task.finish();
      return;
    }
    
    importJavaLibraries(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "library bytecode metric addition";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new BytecodeMetricsImporter(nullerator);
      }
    });
    
    importMavenJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "maven jar bytecode metric addition";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new BytecodeMetricsImporter(nullerator);
      }
    });
    
    importProjectJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "project jar bytecode metric addition";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new BytecodeMetricsImporter(nullerator);
      }
    });
    
    task.finish();
  }
  
  public static void addFindBugs() {
    TaskProgressLogger task = TaskProgressLogger.get();
    task.start("Adding FindBugs results");
   
    ExtractedJavaRepository repo = JavaRepositoryFactory.INSTANCE.loadExtractedJavaRepository(JavaRepositoryFactory.INPUT_REPO);

    if (repo == null) {
      task.finish();
      return;
    }
    
    importJavaLibraries(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "library FindBugs results addition";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new FindBugsImporter(nullerator);
      }
    });
    
    importMavenJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "maven jar FindBugs results addition";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new FindBugsImporter(nullerator);
      }
    });
    
    importProjectJars(repo, new ImporterFactory<ExtractedJarFile>() {
      @Override
      public String message() {
        return "project jar FindBugs results addition";
      }
      
      @Override
      public DatabaseImporter create(Nullerator<ExtractedJarFile> nullerator) {
        return new FindBugsImporter(nullerator);
      }
    });
    
    task.finish();
  }
  
  private static interface ImporterFactory<T> {
    public DatabaseImporter create(Nullerator<T> nullerator);
    public String message();
  }
  
  private static void importJavaLibraries(ExtractedJavaRepository repo, ImporterFactory<ExtractedJarFile> factory) {
    runThreads(Nullerator.createNullerator(repo.getLibraryJarFiles(), "Thread %s now processing: %s"), factory);
  }
  
  private static void importMavenJars(ExtractedJavaRepository repo, ImporterFactory<ExtractedJarFile> factory) {
    runThreads(Nullerator.createNullerator(repo.getMavenJarFiles(), "Thread %s now processing: %s"), factory);
  }
  
  private static void importProjectJars(ExtractedJavaRepository repo, ImporterFactory<ExtractedJarFile> factory) {
    runThreads(Nullerator.createNullerator(repo.getProjectJarFiles(), "Thread %s now processing: %s"), factory);
  }
  
  private static void importProjects(ExtractedJavaRepository repo, ImporterFactory<ExtractedJavaProject> factory) {
    runThreads(Nullerator.createNullerator(repo.getProjects(), "Thread %s now processing: %s"), factory);
  }
  
  private static <T> void runThreads(Nullerator<T> nullerator, ImporterFactory<T> factory) {
    int numThreads = THREAD_COUNT.getValue();
    TaskProgressLogger task = TaskProgressLogger.get();
    
    task.start("Performing " + factory.message() + " with " + numThreads + " threads");
    Collection<Thread> threads = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      DatabaseImporter importer = factory.create(nullerator);
      threads.add(importer.start());
    }
    
    for (Thread t : threads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, "Thread interrupted", e);
      }
    }
    task.finish();
  }
}
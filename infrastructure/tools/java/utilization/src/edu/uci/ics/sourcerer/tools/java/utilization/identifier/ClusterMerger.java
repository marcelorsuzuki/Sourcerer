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
package edu.uci.ics.sourcerer.tools.java.utilization.identifier;

import static edu.uci.ics.sourcerer.util.io.logging.Logging.logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import edu.uci.ics.sourcerer.tools.java.utilization.model.cluster.Cluster;
import edu.uci.ics.sourcerer.tools.java.utilization.model.cluster.ClusterCollection;
import edu.uci.ics.sourcerer.tools.java.utilization.model.cluster.ClusterMatcher;
import edu.uci.ics.sourcerer.tools.java.utilization.model.jar.FqnVersion;
import edu.uci.ics.sourcerer.tools.java.utilization.model.jar.Jar;
import edu.uci.ics.sourcerer.tools.java.utilization.model.jar.JarSet;
import edu.uci.ics.sourcerer.tools.java.utilization.model.jar.VersionedFqnNode;
import edu.uci.ics.sourcerer.util.io.logging.TaskProgressLogger;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class ClusterMerger {
//  public static final RelativeFileArgument CLUSTER_MERGING_LOG = new RelativeFileArgument("cluster-merging-log", null, Arguments.OUTPUT, "Log file containing cluster merging info.");
//  public static final Argument<ClusterMergeMethod> CLUSTER_MERGE_METHOD = new EnumArgument<>("cluster-merge-method", ClusterMergeMethod.class, "Method for performing second stage merge.");
//  
//  public static void mergeClusters(ClusterCollection clusters) {
//    TaskProgressLogger task = TaskProgressLogger.get();
//    
//    task.start("Merging " + clusters.size() + " clusters using " + CLUSTER_MERGE_METHOD.getValue());
//
//    File mergeLog = CLUSTER_MERGING_LOG.getValue();
//    if (mergeLog != null) {
//      task.report("Logging merge information to: " + mergeLog.getPath());
//    }
//    
//    task.start("Sorting clusters by decreasing size");
//    Cluster[] clus = clusters.getClusters().toArray(new Cluster[clusters.size()]);
//    Arrays.sort(clus,
//        new Comparator<Cluster>() {
//          @Override
//          public int compare(Cluster o1, Cluster o2) {
//            int cmp = Integer.compare(o2.getJars().size(), o1.getJars().size());
//            if (cmp == 0) {
//              return Integer.compare(o1.hashCode(), o2.hashCode());
//            } else {
//              return cmp;
//            }
//          }
//        });
//    task.finish();
//    
//    task.start("Merging clusters", "clusters examined", 500);
//    Collection<Cluster> coreClusters = new LinkedList<>();
//    // Go from cluster containing the most jars to the least
//    try (LogFileWriter writer = IOUtils.createLogFileWriter(CLUSTER_MERGING_LOG.getValue())) {
//      for (Cluster biggest : clus) {
//        boolean merged = false;
//        // Find and merge any candidate clusters
//        for (Cluster coreCluster : coreClusters) {
//          // Check if the core cluster should include the next biggest
//          if (CLUSTER_MERGE_METHOD.getValue().shouldMerge(coreCluster, biggest, writer)) {
//            coreCluster.mergeExtra(biggest);
//            merged = true;
//            break;
//          }
//        }
//        if (!merged) {
//          coreClusters.add(biggest);
//        }
//        task.progress();
//      }
//    } catch (IOException e) {
//      logger.log(Level.SEVERE, "Error writing log", e);
//    }
//    task.finish();
//    
//    task.report("Cluster count reduced to " + coreClusters.size());
//    clusters.reset(coreClusters);
//    
//    task.finish();
//  }
  
  public static void mergeByVersions(ClusterCollection clusters) {
    TaskProgressLogger task = TaskProgressLogger.get();
    
    task.start("Merging " + clusters.size() + " clusters by matching versions");
    
    ClusterMatcher matcher = clusters.getClusterMatcher();
    
    TreeSet<Cluster> sortedClusters = new TreeSet<>(Cluster.DESCENDING_SIZE_COMPARATOR);
    sortedClusters.addAll(clusters.getClusters());
    
    Collection<Cluster> remainingClusters = new LinkedList<>();
    task.start("Merging clusters", "clusters examined", 500);
    // Starting from the most important jar
    // For each cluster
    Set<Cluster> mergedClusters = new HashSet<>();
    while (!sortedClusters.isEmpty()) {
      Cluster biggest = sortedClusters.pollFirst();
      // Has this cluster already been merged?
      if (mergedClusters.contains(biggest)) {
        continue;
      }
      remainingClusters.add(biggest);
      
      // Collect the various versions of this cluster that are present
      // A version is a set of versions for each fqn
      Map<Set<FqnVersion>, JarSet> versions = new HashMap<>();
      for (Jar jar : biggest.getJars()) {
        Set<FqnVersion> version = new HashSet<>();
        for (VersionedFqnNode fqn : biggest.getCoreFqns()) {
          version.add(fqn.getVersion(jar));
        }
        JarSet jars = versions.get(version);
        if (jars == null) {
          jars = JarSet.create();
        }
        versions.put(version, jars.add(jar));
      }
      
      Set<VersionedFqnNode> globalPotentials = new HashSet<>();
      Set<VersionedFqnNode> globalPartials = new HashSet<>();
      // For each version, find any fqns that always occur
      for (JarSet jars : versions.values()) {
        Multiset<VersionedFqnNode> potentials = HashMultiset.create();
        for (Jar jar : jars) {
          for (FqnVersion version : jar.getFqns()) {
            potentials.add(version.getFqn());
          }
        }
        
        int max = jars.size();
        for (VersionedFqnNode fqn : potentials.elementSet()) {
          if (potentials.count(fqn) == max && fqn.getJars().isSubset(biggest.getJars())) {
            globalPotentials.add(fqn);
          } else {
            globalPartials.add(fqn);
          }
        }
      }
      
      globalPotentials.removeAll(globalPartials);
      globalPotentials.removeAll(biggest.getCoreFqns());
      // Collect the clusters we plan on merging
      Set<Cluster> clustersToMerge = new HashSet<>();
      for (VersionedFqnNode fqn : globalPotentials) {
        clustersToMerge.add(matcher.getCluster(fqn));
      }
      clustersToMerge.removeAll(mergedClusters);
      // Now merge the clusters
      for (Cluster cluster : clustersToMerge) {
        for (VersionedFqnNode fqn : cluster.getCoreFqns()) {
          biggest.addVersionedCore(fqn);
        }
      }
      mergedClusters.addAll(clustersToMerge);
      
      task.progress();
    }
    
    
    task.finish();
    
    clusters.reset(remainingClusters);
    task.report(clusters.size() + " clusters remain");
    task.finish();
  }
}

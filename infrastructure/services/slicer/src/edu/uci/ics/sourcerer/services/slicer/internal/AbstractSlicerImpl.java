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
package edu.uci.ics.sourcerer.services.slicer.internal;

import static edu.uci.ics.sourcerer.util.io.logging.Logging.logger;

import java.lang.reflect.Modifier;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;

import edu.uci.ics.sourcerer.services.slicer.model.Slice;
import edu.uci.ics.sourcerer.services.slicer.model.Slicer;
import edu.uci.ics.sourcerer.tools.java.model.types.Entity;
import edu.uci.ics.sourcerer.tools.java.model.types.Relation;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public abstract class AbstractSlicerImpl implements Slicer {
  
	public static final boolean lookRecursion = false;
	
  AbstractSlicerImpl() {}
  
  protected abstract SlicerDatabaseAccessor getAccessor();
  
  @Override
  public Slice slice(Set<Integer> seeds) {
    return new SlicerInstance(seeds).slice();
  }
    
  private class SlicerInstance {
    private final SlicerDatabaseAccessor db;
    private final SliceImpl slice;
    private final NovelQueue queue;
    
    public SlicerInstance(Set<Integer> seeds) {
      db = getAccessor();
      slice = new SliceImpl();
      queue = new NovelQueue(seeds);
    }
    
    
    public boolean isJREEntity(SlicedEntityImpl e){
    	String className = e.getFqn();
    	className=className.substring(0, className.lastIndexOf('.'));
    	try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException e1) {
			FelipeDebug.debug(e1.getMessage());
			return false;
		}    	
    }
    public Slice slice() {
//      slice.addBasicLibraryTypes(db);
      while (queue.hasMore()) {
        while (queue.hasMore()) {
          SlicedEntityImpl entity = queue.poll();
          switch (entity.getEntityType()) {
            case FIELD:
            case ENUM_CONSTANT:
            case INITIALIZER:
            case METHOD:
            case CONSTRUCTOR:
              boolean willLookRecursion = !isJREEntity(entity) || lookRecursion;
              FelipeDebug.debug("["+entity.getEntityType()+"]:"+entity.getFqn()+ " ("+entity.getEntityID()+")[willAdd="+willLookRecursion+"]");
        	  addContainingEntity(entity.getEntityID());
        	  addUsedTypes(entity.getEntityID());
        	  //addInstanciedTypes(entity.getEntityID());
              if(willLookRecursion){
            	  addCalledMethods(entity.getEntityID());
            	  addWrittedEntities(entity.getEntityID());
            	  addReadEntities(entity.getEntityID());
              }
              break;
            case CLASS:
            case INTERFACE:
            case ENUM:
              addContainingEntity(entity.getEntityID());
              //addUsedTypes(entity.getEntityID());
              addCalledMethods(entity.getEntityID());
              if (slice.isInternal(entity.getEntityID())) {
                addInitializers(entity.getEntityID());
                addConstructors(entity.getEntityID());
              }
              break;
            case ANNOTATION:
            case ANNOTATION_ELEMENT:
              break;
          }
        }
      }
      
//      for (SlicedEntityImpl entity : slice.getExternalEntities().toArray(new SlicedEntityImpl[slice.getExternalEntities().size()])) {
//        // Is it a type declaration?
//        if (entity.getEntityType() == Entity.CLASS ||
//            entity.getEntityType() == Entity.INTERFACE ||
//            entity.getEntityType() == Entity.ENUM) {
//          addTypeHierarchy(exploreTypeHierarchy(entity.getEntityID()));
//        }
//      }
      
      for (SlicedEntityImpl entity : slice.getInternalEntities().toArray(new SlicedEntityImpl[slice.getInternalEntities().size()])) {
        // Is it a type declaration?
        if (entity.getEntityType() == Entity.CLASS ||
            entity.getEntityType() == Entity.INTERFACE ||
            entity.getEntityType() == Entity.ENUM) {
          checkTypeHierarchy(exploreTypeHierarchy(entity.getEntityID()));
//          exploreTypeHierarchy(entity.getEntityID());
        }
      }
      
      for (SlicedFileImpl file : slice.getFiles()) {
        if (file.getImports() == null) {
          file.setImports(db.getImports(file.getFileID()));
        }
      }
      
      return slice;
    }
    
    private void addInstanciedTypes(Integer entityID) {
    	queue.addAll(db.getRelationTargetsBySource(Relation.INSTANTIATES, entityID));
	}

	private void addReadEntities(Integer entityID) {
		queue.addAll(db.getRelationTargetsBySource(Relation.READS, entityID));
	}

	private void addWrittedEntities(Integer entityID) {
    	queue.addAll(db.getRelationTargetsBySource(Relation.WRITES, entityID));
		
	}

	private void addContainingEntity(Integer entityID) {
      queue.addAll(db.getRelationSourcesByTarget(Relation.CONTAINS, entityID));
    }
    
    private void addUsedTypes(Integer entityID) {
      queue.addAll(db.getRelationTargetsBySource(Relation.USES, entityID));
    }
    private void addCalledMethods(Integer entityID) {
      queue.addAll(db.getRelationTargetsBySource(Relation.CALLS, entityID));
    }
    
    private void addInitializers(Integer entityID) {
      queue.addAllEntities(db.getContained(Entity.INITIALIZER, entityID));
    }
    
    private void addConstructors(Integer entityID) {
      queue.addAllEntities(db.getContained(Entity.CONSTRUCTOR, entityID));
    }
    
    private ModeledTypeImpl exploreTypeHierarchy(Integer entityID) {
      ModeledTypeImpl type = slice.getType(entityID);
      if (type == null) {
        type = new ModeledTypeImpl(entityID);
        slice.addType(type);
        
        // add the superclass
        for (Integer superType : db.getRelationTargetsBySource(Relation.EXTENDS, entityID)) {
          if (type.getSuperclass() == null) {
            type.setSuperClass(exploreTypeHierarchy(superType));
          } else {
            logger.severe("Multiple supertypes for " + entityID);
          }
        }
        
        // add the super interfaces
        for (Integer superInterface : db.getRelationTargetsBySource(Relation.IMPLEMENTS, entityID)) {
          type.addSuperInterace(exploreTypeHierarchy(superInterface));
        }
      }
      return type;
    }
    
    private boolean checkTypeHierarchy(ModeledTypeImpl type) {
      // Am I java.lang.Object?
      if (type.getSuperclass() == null) {
        return false;
      }
      // Am I in the slice?
      else if (slice.contains(type.getEntityID())) {
        return true;
      } else {
        boolean willBeInSlice = false;
        
        // Do I have a superclass in the slice?
        if (checkTypeHierarchy(type.getSuperclass())) {
          // I should be in the slice!
          queue.add(type.getEntityID());
          willBeInSlice = true;
        }
        
        // Do I have a super interface in the slice?
        for (ModeledTypeImpl superInterface : type.getSuperInterfaces()) {
          if (checkTypeHierarchy(superInterface)) {
            // I should be in the slice!
            queue.add(type.getEntityID());
            willBeInSlice = true;
          }
        }
        
        return willBeInSlice;
      }
    }
    
    private void addTypeHierarchy(ModeledTypeImpl type) {
      if (type != null) {
        queue.add(type.getEntityID());
        // Add my superclass to the slice
        addTypeHierarchy(type.getSuperclass());
        
        // Do I have a super interface in the slice?
        for (ModeledTypeImpl superInterface : type.getSuperInterfaces()) {
          addTypeHierarchy(superInterface);
        }
      }
    }
    
    private class NovelQueue {
      private Deque<SlicedEntityImpl> queue;
      
      private NovelQueue(Set<Integer> seeds) {
        queue = new LinkedList<>();
        
        LinkedList<Integer> todo = new LinkedList<>(seeds);
        while (!todo.isEmpty()) {	//check for dependencies, like call to other methods, ...
          Integer entityID = todo.poll();	//get next required entity id to work on it
          SlicedEntityImpl entity = db.getEntity(entityID); //get the entity itself
          if (entity.getEntityType() != Entity.PARAMETER && entity.getEntityType() != Entity.LOCAL_VARIABLE) {//if it is not a parameter or local variable
            slice.addProject(entity.getProjectID());//add its project
            queue.add(entity);						//add to the queue
            slice.add(entity);						//add slice
            todo.addAll(db.getRelationTargetsBySource(Relation.CONTAINS, entityID));
            todo.addAll(db.getRelationTargetsBySource(Relation.WRITES, entityID));//added by Felipe flp313@gmail.com
          }
        }
      }
      
      public void addAll(Iterable<? extends Integer> entityIDs) {
        for (Integer entityID : entityIDs) {
          add(entityID);
        }
      }
      
      public void add(Integer entityID) {
        if (!slice.contains(entityID)) {
          SlicedEntityImpl entity = db.getEntity(entityID);
          queue.add(entity);
          slice.add(entity);
          
        }
      }

	public void addAllEntities(Iterable<? extends SlicedEntityImpl> entities) {
        for (SlicedEntityImpl entityID : entities) {
          add(entityID);
        }
      }
      
      public void add(SlicedEntityImpl entity) {
        if (!slice.contains(entity.getEntityID())) {
          queue.add(entity);
          slice.add(entity);
        }
      }
      
      public SlicedEntityImpl poll() {
        return queue.poll();
      }
      
      public boolean hasMore() {
        return !queue.isEmpty();
      }
    }
  }
}

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import edu.uci.ics.sourcerer.services.file.adapter.FileAdapter;
import edu.uci.ics.sourcerer.services.slicer.SlicerFactory;
import edu.uci.ics.sourcerer.services.slicer.model.Slice;
import edu.uci.ics.sourcerer.tools.java.model.types.Entity;
import edu.uci.ics.sourcerer.tools.java.model.types.Modifier;
import edu.uci.ics.sourcerer.tools.java.repo.model.JavaRepositoryFactory;
import edu.uci.ics.sourcerer.util.io.IOUtils;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class SliceImpl implements Slice {
	private final Set<Integer> projects;
	private final Map<Integer, SlicedEntityImpl> internalEntities;
	private final Map<Integer, SlicedEntityImpl> externalEntities;
	private final Map<Integer, SlicedFileImpl> files;
	private final Map<Integer, ModeledTypeImpl> typeModel;

	SliceImpl() {
		projects = new HashSet<>();
		internalEntities = new HashMap<>();
		externalEntities = new HashMap<>();
		files = new HashMap<>();
		typeModel = new HashMap<>();
	}

	void addProject(Integer projectID) {
		projects.add(projectID);
	}

	void add(SlicedEntityImpl entity) {
		if (projects.contains(entity.getProjectID())) {
			internalEntities.put(entity.getEntityID(), entity);
			if (isSliceable(entity.getEntityType()) ){
				SlicedFileImpl file = files.get(entity.getFileID());
				if (file == null) {
					file = new SlicedFileImpl(entity.getFileID());
					files.put(entity.getFileID(), file);
				}
				file.addEntity(entity);
			} 
		} else {
			externalEntities.put(entity.getEntityID(), entity);
		}
	}

	private static boolean isSliceable(Entity type) {
		return 
				type == Entity.CLASS || 
				type == Entity.INTERFACE ||
				type == Entity.ENUM ||
				type == Entity.FIELD ||
				type == Entity.ENUM_CONSTANT ||
				type == Entity.INITIALIZER ||
				type == Entity.METHOD || 
				//false;
				type == Entity.CONSTRUCTOR;
	}

	ModeledTypeImpl getType(Integer entityID) {
		return typeModel.get(entityID);
	}

	void addType(ModeledTypeImpl type) {
		typeModel.put(type.getEntityID(), type);
	}

	boolean contains(Integer entityID) {
		return externalEntities.containsKey(entityID) || internalEntities.containsKey(entityID);
	}

	SlicedEntityImpl get(Integer entityID) {
		SlicedEntityImpl entity = internalEntities.get(entityID);
		if (entity == null) {
			return externalEntities.get(entityID);
		} else {
			return entity;
		}
	}

	boolean isInternal(Integer entityID) {
		return internalEntities.containsKey(entityID);
	}

	boolean isExternal(Integer entityID) {
		return externalEntities.containsKey(entityID);
	}

	public Collection<? extends SlicedEntityImpl> getInternalEntities() {
		return internalEntities.values();
	}

	public Collection<? extends SlicedEntityImpl> getExternalEntities() {
		return internalEntities.values();
	}

	public Collection<? extends SlicedFileImpl> getFiles() {
		return files.values();
	}

	private byte[] getContents(Integer fileID) {
		if (SlicerFactory.FILE_SERVER_URL.hasValue()) {
			return IOUtils.wget(SlicerFactory.FILE_SERVER_URL.getValue() + "?fileID=" + fileID); 
		} else if (JavaRepositoryFactory.INPUT_REPO.hasValue()) {
			return FileAdapter.lookupByFileID(fileID);
		} else {
			return null;
		}
	}

	@Override
	public byte[] toZipFile() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(bos)) {
			// For each file
			for (SlicedFileImpl file : files.values()) {
				FelipeDebug.debug("[File]:"+file.getFileID());
				for (SlicedEntityImpl e : file.getEntities()){
					FelipeDebug.debug("[Entity]"+e.getEntityType().name()+":" + e.getFqn());
				}
				// The first entry should always be a declared type
				String fqn = file.getEntities().iterator().next().getFqn();
				FelipeDebug.debug("[File]:"+fqn);

				// Get the file contents
				char[] contents = new String(getContents(file.getFileID())).toCharArray();

				StringBuilder builder = new StringBuilder();

				// Add the package
				int idx = fqn.lastIndexOf('.');
				builder.append("package ").append(fqn.substring(0, idx)).append(";\n\n");

				// Add the imports
				for (SlicedImportImpl imp : file.getImports()) {
					// Is the imported entity in the slice?
					if (contains(imp.getEid())) {
						builder.append(contents, imp.getOffset(), imp.getLength()).append('\n');
					}
				}

				builder.append('\n');

				Deque<Integer> endDeclared = new LinkedList<>();

				for (SlicedEntityImpl entity: file.getEntities()) {
					// Is the start of this after the last declared?
					//TODO test cases for this "if" ADDED BY FELIPE 
					if(entity.getOffset()==null){
						continue;
					}
					if (!endDeclared.isEmpty() && entity.getOffset() > endDeclared.peek()) {
						builder.append("}\n");
						endDeclared.pop();
					}
					
					if(entity.getEntityType().is(Entity.FIELD)){
						String modf = "";
						for (Modifier mod : entity.getModifiers()) {
							builder.append(mod).append(' ');
							modf+=mod+" ";
						}
						FelipeDebug.debug("[Field]:"+modf+" "+entity);
						//builder.append((Modifiers.toString(entity.getModifiers().getValue())));
						//builder.append("Object ");//type
						
						String select = "select fqn from entities left join relations on (rhs_eid=entity_id) where relation_type='HOLDS' AND lhs_eid="+entity.getEntityID();
						FelipeDebug.debug(select+";");
						SlicerDatabaseAccessor dba = SlicerDatabaseAccessor.create();
						String typeFqn=dba.manualQuery(select);
						if(typeFqn==null || typeFqn==""){
							typeFqn=".Object";
						}
						builder.append(typeFqn.substring(typeFqn.lastIndexOf(".")+1)+" ");//TYPE
						
						//name and value
						int len = entity.getLength();
						if(entity.getEntityType().is(Entity.FIELD) 
								//&& entity.getModifiers()!=null 
								//&& (entity.getModifiers().getValue() & Modifier.FINAL.getValue() ) !=0 ){//it is a field and it is final...so it should be initialized
								){
							len+= getInitFieldLength(contents,entity.getOffset()+len);//new len based on source file
							builder.append(contents, entity.getOffset(),len).append("\n");//name and value
						}  else {
							builder.append(entity.getFqn().substring(entity.getFqn().lastIndexOf(".")+1)+";\n");//name
						}
												
					} else if (entity.getEntityType().isDeclaredType()) {
						// Add the modifiers
						for (Modifier mod : entity.getModifiers()) {
							builder.append(mod).append(' ');
						}
						// Add the type name
						builder.append(entity.getEntityType()).append(' ').append(fqn.substring(idx + 1));

						
						ModeledTypeImpl type = typeModel.get(
								entity.getEntityID());

						if (entity.getEntityType() == Entity.CLASS) {
							// Add the superclass
							ModeledTypeImpl superclassType = type.getSuperclass();
							// Ignore it if it's java.lang.Object
							if (superclassType.getSuperclass() != null && contains(superclassType.getEntityID())) {
								builder.append(" extends ").append(get(superclassType.getEntityID()).getFqn()).append(' ');
							}

							// Add the superinterfaces
							boolean first = true;
							for (ModeledTypeImpl superInterfaceType : type.getSuperInterfaces()) {
								if (contains(superInterfaceType.getEntityID())) {
									if (first) {
										builder.append(" implements ");
										first = false;
									} else {
										builder.append(", ");
									}
									builder.append(get(superInterfaceType.getEntityID()).getFqn());
								}
							}
						} else if (entity.getEntityType() == Entity.INTERFACE) {
							// Add the superinterfaces
							boolean first = true;
							for (ModeledTypeImpl superInterfaceType : type.getSuperInterfaces()) {
								if (contains(superInterfaceType.getEntityID())) {
									if (first) {
										builder.append(" extends ");
										first = false;
									} else {
										builder.append(", ");
									}
									builder.append(get(superInterfaceType.getEntityID()).getFqn());
								}
							}
						}

						builder.append(" {\n");
						endDeclared.push(entity.getOffset() + entity.getLength());
					} else {
						// Add this entity
						builder.append(
								contents, 
								entity.getOffset(),
								entity.getLength())
								.append("\n");

					}
				}

				while (!endDeclared.isEmpty()) {
					builder.append("}\n");
					endDeclared.pop();
				}
				zos.putNextEntry(new ZipEntry(fqn.replace('.', '/') + ".java"));
				zos.write(builder.toString().getBytes());
				zos.closeEntry();
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error in writing slice to zip file");
		}
		return bos.toByteArray();
	}


	
	
	/**get the length that it takes to add a fields value, for example: str = new String("abc"); is 21 chars long, " = new String("abc");"*/
	private static int getInitFieldLength(char[] contents, int start) {		
		char[] open = {'{','[','('};
		char[] close = {'}',']',')'};		
		char finish = ';';
		LinkedList<Integer> openned = new LinkedList<Integer>();
		int count = 0;
		boolean end = false;
		while(!end && contents.length>(start+count)){
			char c = contents[start+count];
			if(c==finish && openned.size()==0 ){
				end=true;
				return count+1;
			}
			int openidx = -1;
			for(int i=0;i<open.length;++i){
				if(c==open[i]){
					openidx=i;
					break;
				}
			}
			int closeidx = -1;
			for(int i=0;i<close.length;++i){
				if(c==close[i]){
					closeidx=i;
					break;
				}
			}
			if(openidx>=0){
				openned.addFirst(openidx);
			} else if(openned.size()>0 && closeidx==openned.getFirst()){
				openned.removeFirst();
			}
			count++;
		}
		return 0;
	}
}

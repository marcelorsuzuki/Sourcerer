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
package edu.uci.ics.sourcerer.services.slicer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;

import javax.swing.JOptionPane;


import edu.uci.ics.sourcerer.services.slicer.internal.FelipeDebug;
import edu.uci.ics.sourcerer.services.slicer.internal.SlicerImpl;
import edu.uci.ics.sourcerer.services.slicer.internal.SliceImpl;
import edu.uci.ics.sourcerer.services.slicer.model.Slice;
import edu.uci.ics.sourcerer.services.slicer.model.Slicer;
import edu.uci.ics.sourcerer.util.io.arguments.Argument;
import edu.uci.ics.sourcerer.util.io.arguments.StringArgument;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class SlicerFactory {
	
	public static final Argument<String> FILE_SERVER_URL = new StringArgument("file-server-url", "URL for the file server");
	
	public static Slicer createSlicer() {
		return SlicerImpl.create();
	}

	
	

	
	public static void main(String[] args) {
		Integer entityID = 8331277;//8537791;//8512696;//8512634;//8347531;
		long start = System.currentTimeMillis();
		Slicer slicer = SlicerFactory.createSlicer();
		Slice result = slicer.slice(Collections.singleton(entityID));
		SliceImpl si = (SliceImpl)result;
		FelipeDebug.debug("[SlicerFactory]slice:\n"+si.getInternalEntities().toString().replace(",", "\n").replace("[","").replace("]", ""));
		byte[] input = result.toZipFile();
		long end = System.currentTimeMillis();
		System.out.println("total of "+(end-start)+" ms");
		String filename = JOptionPane.showInputDialog("Type zip's fqn",System.getProperty("user.home")+File.separator+"slicertest.zip");
		JOptionPane.showMessageDialog(null, result.getInternalEntities().toString().replace(",", "\n").trim());
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(new File(filename));
			fos.write(input);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
}

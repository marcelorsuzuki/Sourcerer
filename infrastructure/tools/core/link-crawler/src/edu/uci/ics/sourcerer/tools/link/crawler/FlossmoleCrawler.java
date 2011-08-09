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
package edu.uci.ics.sourcerer.tools.link.crawler;

import static edu.uci.ics.sourcerer.util.io.Logging.logger;

import java.io.IOException;
import java.util.logging.Level;

import edu.uci.ics.sourcerer.tools.link.crawler.flossmole.GoogleCodeProjects;
import edu.uci.ics.sourcerer.tools.link.model.Project;
import edu.uci.ics.sourcerer.tools.link.model.Source;
import edu.uci.ics.sourcerer.util.io.EntryWriter;
import edu.uci.ics.sourcerer.util.io.IOUtils;
import edu.uci.ics.sourcerer.util.io.SimpleSerializer;
import edu.uci.ics.sourcerer.util.io.arguments.DualFileArgument;
import edu.uci.ics.sourcerer.utils.db.DatabaseRunnable;
import edu.uci.ics.sourcerer.utils.db.sql.ISelectQuery;
import edu.uci.ics.sourcerer.utils.db.sql.ITypedQueryResult;


/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class FlossmoleCrawler {
  public static final DualFileArgument GOOGLE_CODE_LIST = new DualFileArgument("google-code-list", "google-code-list.txt", "File containing list of crawled Google Code projects.");
  
  private FlossmoleCrawler() {
  }
  
  public static void crawlGoogleCode() {
    new DatabaseRunnable() {
      @Override
      public void action() {
        ISelectQuery select = exec.makeSelectQuery(GoogleCodeProjects.TABLE);
        select.addSelect(GoogleCodeProjects.PROJECT_NAME);
        ITypedQueryResult result = select.select();
        
        SimpleSerializer writer = null;
        EntryWriter<Project> ew = null;
        Project project = new Project();
        try {
          writer = IOUtils.makeSimpleSerializer(GOOGLE_CODE_LIST);
          ew = writer.getEntryWriter(Project.class);
          while (result.next()) {
            String name = result.getResult(GoogleCodeProjects.PROJECT_NAME);
            project.set(name, "http://" + name + ".googlecode.com/svn" , Source.GOOGLE_CODE);
            ew.write(project);
            ew.flush();
          }
        } catch (IOException e) {
          logger.log(Level.SEVERE, "Error writing to file.", e);
        } finally {
          IOUtils.close(ew, writer);
        }
        
      }
    }.run();
  }
}

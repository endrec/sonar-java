/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.sonar.api.BatchExtension;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannersFactory;

import javax.annotation.Nullable;

public class SonarComponents implements BatchExtension {

  private final FileLinesContextFactory fileLinesContextFactory;
  private final ResourcePerspectives resourcePerspectives;
  private final JavaFileScannersFactory[] fileScannersFactories;

  public SonarComponents(FileLinesContextFactory fileLinesContextFactory, ResourcePerspectives resourcePerspectives) {
    this(fileLinesContextFactory, resourcePerspectives, null);
  }

  public SonarComponents(FileLinesContextFactory fileLinesContextFactory, ResourcePerspectives resourcePerspectives, @Nullable JavaFileScannersFactory[] fileScannersFactories) {
    this.fileLinesContextFactory = fileLinesContextFactory;
    this.resourcePerspectives = resourcePerspectives;
    this.fileScannersFactories = fileScannersFactories;
  }

  public FileLinesContextFactory getFileLinesContextFactory() {
    return fileLinesContextFactory;
  }

  public ResourcePerspectives getResourcePerspectives() {
    return resourcePerspectives;
  }

  public Iterable<JavaFileScanner> createJavaFileScanners() {
    Iterable<JavaFileScanner> result = ImmutableList.of();
    if (fileScannersFactories != null) {
      for (JavaFileScannersFactory factory : fileScannersFactories) {
        result = Iterables.concat(result, factory.createJavaFileScanners());
      }
    }
    return result;
  }

}

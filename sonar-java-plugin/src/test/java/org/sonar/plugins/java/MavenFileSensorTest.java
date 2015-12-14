/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.java;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleAnnotationUtils;
import org.sonar.java.SonarComponents;
import org.sonar.java.checks.BadMethodName_S00100_Check;
import org.sonar.java.checks.maven.PomElementOrderCheck;
import org.sonar.maven.MavenCheck;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.squidbridge.api.CodeVisitor;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MavenFileSensorTest {

  private DefaultFileSystem fileSystem;
  private MavenFileSensor sensor;

  @Before
  public void setUp() {
    fileSystem = new DefaultFileSystem(null);
    sensor = new MavenFileSensor(mock(SonarComponents.class), fileSystem);
  }

  @Test
  public void to_string() {
    assertThat(sensor.toString()).isEqualTo("MavenFileSensor");
  }

  @Test
  public void should_execute_on_project_having_xml() {
    Project project = mock(Project.class);
    fileSystem.add(new DefaultInputFile("fake.java").setLanguage(Java.KEY));
    assertThat(sensor.shouldExecuteOnProject(project)).isFalse();

    fileSystem.add(new DefaultInputFile("fake.xml").setLanguage(Java.KEY));
    assertThat(sensor.shouldExecuteOnProject(project)).isFalse();

    fileSystem.add(new DefaultInputFile("myModule/pom.xml").setLanguage(Java.KEY));
    assertThat(sensor.shouldExecuteOnProject(project)).isTrue();
  }

  @Test
  public void test_issues_creation() throws Exception {
    DefaultFileSystem fs = new DefaultFileSystem(new File(""));
    File file = new File("src/test/files/maven/pom.xml");
    fs.add(new DefaultInputFile(file.getPath()).setFile(file).setLanguage(Java.KEY));
    CodeVisitor mavenCheck = new PomElementOrderCheck();
    CodeVisitor javaCheck = new BadMethodName_S00100_Check();
    SonarComponents sonarComponents = createSonarComponentsMock(fs, mavenCheck, javaCheck);
    MavenFileSensor mps = new MavenFileSensor(sonarComponents, fs);

    SensorContext context = mock(SensorContext.class);
    when(context.getResource(any(InputPath.class))).thenReturn(org.sonar.api.resources.File.create("src/test/files/maven/pom.xml"));

    mps.analyse(mock(Project.class), context);

    verify(sonarComponents, times(1)).addIssue(eq(file.getAbsoluteFile()), any(MavenCheck.class), any(Integer.class), anyString(), isNull(Double.class));
  }

  @Test
  public void no_analysis_when_no_pom_provided() throws Exception {
    DefaultFileSystem fs = new DefaultFileSystem(new File(""));
    CodeVisitor mavenCheck = new PomElementOrderCheck();
    SonarComponents sonarComponents = createSonarComponentsMock(fs, mavenCheck);
    MavenFileSensor mps = new MavenFileSensor(sonarComponents, fs);

    SensorContext context = mock(SensorContext.class);

    mps.analyse(mock(Project.class), context);

    verify(sonarComponents, never()).addIssue(any(File.class), any(MavenCheck.class), any(Integer.class), anyString(), isNull(Double.class));
  }

  @Test
  public void no_analysis_when_no_pom_nor_pom_scanner_provided() {
    DefaultFileSystem fs = new DefaultFileSystem(new File(""));
    CodeVisitor javaCheck = new BadMethodName_S00100_Check();
    SonarComponents sonarComponents = createSonarComponentsMock(fs, javaCheck);
    MavenFileSensor mps = new MavenFileSensor(sonarComponents, fs);

    SensorContext context = mock(SensorContext.class);

    mps.analyse(mock(Project.class), context);

    verify(sonarComponents, never()).addIssue(any(File.class), any(MavenCheck.class), any(Integer.class), anyString(), isNull(Double.class));
  }

  @Test
  public void no_analysis_when_no_pom_scanner_provided() throws Exception {
    DefaultFileSystem fs = new DefaultFileSystem(new File(""));
    File file = new File("src/test/files/maven/pom.xml");
    fs.add(new DefaultInputFile(file.getPath()).setFile(file).setLanguage(Java.KEY));
    CodeVisitor javaCheck = new BadMethodName_S00100_Check();
    SonarComponents sonarComponents = createSonarComponentsMock(fs, javaCheck);
    MavenFileSensor mps = new MavenFileSensor(sonarComponents, fs);

    SensorContext context = mock(SensorContext.class);
    when(context.getResource(any(InputPath.class))).thenReturn(org.sonar.api.resources.File.create("src/test/files/maven/pom.xml"));

    mps.analyse(mock(Project.class), context);

    verify(sonarComponents, never()).addIssue(any(File.class), any(MavenCheck.class), any(Integer.class), anyString(), isNull(Double.class));
  }

  private static SonarComponents createSonarComponentsMock(DefaultFileSystem fs, CodeVisitor... codeVisitor) {
    SonarComponents sonarComponents = mock(SonarComponents.class);
    when(sonarComponents.checkClasses()).thenReturn(codeVisitor);

    when(sonarComponents.getFileSystem()).thenReturn(fs);

    Checks<JavaCheck> checks = mock(Checks.class);
    when(checks.ruleKey(any(JavaCheck.class))).thenReturn(RuleKey.of("squid", RuleAnnotationUtils.getRuleKey(PomElementOrderCheck.class)));
    when(sonarComponents.checks()).thenReturn(Lists.<Checks<JavaCheck>>newArrayList(checks));

    return sonarComponents;
  }
}
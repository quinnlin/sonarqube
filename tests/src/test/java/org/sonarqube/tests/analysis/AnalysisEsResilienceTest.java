/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonarqube.tests.analysis;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarScanner;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonarqube.tests.Byteman;
import org.sonarqube.tests.Tester;
import org.sonarqube.ws.Organizations.Organization;
import org.sonarqube.ws.WsComponents;
import org.sonarqube.ws.WsUsers.CreateWsResponse.User;
import org.sonarqube.ws.client.component.SearchWsRequest;
import util.ItUtils;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static util.ItUtils.projectDir;

public class AnalysisEsResilienceTest {

  @ClassRule
  public static final Orchestrator orchestrator;

  static {
    orchestrator = Byteman.enableScript(Orchestrator.builderEnv(), "resilience/active_rule_indexer.btm")
      .addPlugin(ItUtils.xooPlugin())
      .build();
  }

  @Rule
  public Tester tester = new Tester(orchestrator);

  @Test
  public void activation_and_deactivation_of_rule_is_resilient_to_indexing_errors() throws Exception {
    String projectKey = randomAlphanumeric(20);
    Organization organization = tester.organizations().generate();
    User orgAdministrator = tester.users().generateAdministrator(organization);
    assertThat(searchComponents(projectKey, organization)).isEmpty();

    executeAnalysis(projectKey, organization, orgAdministrator);
    assertThat(searchComponents(projectKey, organization)).isNotEmpty();
  }

  private List<String> searchComponents(String projectKey, Organization organization) {
    SearchWsRequest query = new SearchWsRequest()
      .setOrganization(organization.getKey())
      .setQualifiers(singletonList("TRK"))
      .setQuery(projectKey);
    return tester.wsClient().components().search(query)
      .getComponentsList()
      .stream()
      .map(WsComponents.Component::getKey)
      .collect(Collectors.toList());
  }

  private String executeAnalysis(String projectKey, Organization organization, User orgAdministrator) {
    BuildResult buildResult = orchestrator.executeBuild(SonarScanner.create(projectDir("shared/xoo-sample"),
      "sonar.organization", organization.getKey(),
      "sonar.projectKey", projectKey,
      "sonar.login", orgAdministrator.getLogin(),
      "sonar.password", orgAdministrator.getLogin()));
    return ItUtils.extractCeTaskId(buildResult);
  }


}

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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonarqube.tests.Byteman;
import org.sonarqube.tests.Tester;
import org.sonarqube.ws.Organizations.Organization;
import org.sonarqube.ws.WsUsers.CreateWsResponse.User;
import org.sonarqube.ws.client.component.SuggestionsWsRequest;
import util.ItUtils;

import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static util.ItUtils.projectDir;

public class AnalysisEsResilienceTest {

  @ClassRule
  public static final Orchestrator orchestrator;

  static {
    orchestrator = Byteman.enableScript(Orchestrator.builderEnv(), "resilience/making_ce_indexation_failing.btm")
//    orchestrator = Orchestrator.builderEnv()
      .addPlugin(ItUtils.xooPlugin())
      .build();
  }

  @Rule
  public Tester tester = new Tester(orchestrator);

  @Test
  public void activation_and_deactivation_of_rule_is_resilient_to_indexing_errors() throws Exception {
    String projectKey = randomAlphanumeric(20);
    String fileKey = projectKey + ":src/main/xoo/sample/Sample.xoo";

    Organization organization = tester.organizations().generate();
    User orgAdministrator = tester.users().generateAdministrator(organization);
    assertThat(searchFile(projectKey, organization)).isEmpty();

    //FIXME magically let indexation fail
    executeAnalysis(projectKey, organization, orgAdministrator);
    assertThat(searchFile(fileKey, organization)).isEmpty();

    //FIXME magically let indexation work fine
    executeAnalysis(projectKey, organization, orgAdministrator);
    assertThat(searchFile(fileKey, organization)).isNotEmpty();
  }

  private List<String> searchFile(String key, Organization organization) {
    SuggestionsWsRequest query = SuggestionsWsRequest.builder()
      .setS(key)
      .build();
    Map<String, Object> response = ItUtils.jsonToMap(
      tester.wsClient().components().suggestions(query).content()
    );
    List results = (List) response.get("results");
    Map trkResult = (Map) results.stream().filter(result -> "FIL".equals(((Map) result).get("q"))).findAny().get();
    List items = (List) trkResult.get("items");
    Stream<String> x = items.stream().map(item -> (String) ((Map) item).get("key"));
    return x.collect(Collectors.toList());
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

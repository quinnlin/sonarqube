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
// @flow
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import App from './App';
import throwGlobalError from '../../../app/utils/throwGlobalError';
import {
  getComponent,
  getCurrentUser,
  getMetrics,
  getMetricsKey
} from '../../../store/rootReducer';
import { fetchMetrics } from '../../../store/rootActions';
import { getMeasuresAndMeta } from '../../../api/measures';
import { getLeakPeriod } from '../../../helpers/periods';
import { enhanceMeasure } from '../../../components/measure/utils';
import type { Component, Period } from '../types';
import type { Measure, MeasureEnhanced } from '../../../components/measure/types';

const mapStateToProps = (state, ownProps) => ({
  component: getComponent(state, ownProps.location.query.id),
  currentUser: getCurrentUser(state),
  metrics: getMetrics(state),
  metricsKey: getMetricsKey(state)
});

const banQualityGate = (component: Component): Array<Measure> => {
  const bannedMetrics = [];
  if (!['VW', 'SVW'].includes(component.qualifier)) {
    bannedMetrics.push('alert_status');
  }
  if (component.qualifier === 'APP') {
    bannedMetrics.push('releasability_rating', 'releasability_effort');
  }
  return component.measures.filter(measure => !bannedMetrics.includes(measure.metric));
};

const fetchMeasures = (component: string, metricsKey: Array<string>) => (
  dispatch,
  getState
): Promise<{ component: Component, measures: Array<MeasureEnhanced>, leakPeriod: ?Period }> => {
  if (metricsKey.length <= 0) {
    return Promise.resolve({ component: {}, measures: [], leakPeriod: null });
  }

  return getMeasuresAndMeta(component, metricsKey, { additionalFields: 'periods' }).then(r => {
    const measures: Array<MeasureEnhanced> = banQualityGate(r.component).map(measure =>
      enhanceMeasure(measure, getMetrics(getState()))
    );

    const newBugs = measures.find(measure => measure.metric.key === 'new_bugs');
    const applicationPeriods = newBugs ? [{ index: 1 }] : [];
    const periods = r.component.qualifier === 'APP' ? applicationPeriods : r.periods;
    return { component: r.component, measures, leakPeriod: getLeakPeriod(periods) };
  }, throwGlobalError);
};

const mapDispatchToProps = { fetchMeasures, fetchMetrics };

export default connect(mapStateToProps, mapDispatchToProps)(withRouter(App));

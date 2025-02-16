import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const contentService = {
  localizeContent: (content, targetRegions) =>
    api.post('/localization/localize', { content, targetRegions }),

  getRegionalPerformance: (contentId, regions) =>
    api.get(`/localization/performance/${contentId}`, { params: { regions } }),

  getRegionalStrategy: (region, industry) =>
    api.get(`/localization/strategy/${region}`, { params: { industry } }),
};

export const competitorService = {
  analyzeCompetitors: (industry, competitors) =>
    api.post('/competitor-analysis/analyze', competitors, { params: { industry } }),

  getCompetitiveAdvantage: (industry) =>
    api.get(`/competitor-analysis/competitive-advantage/${industry}`),

  predictCompetitorMoves: (competitor, industry) =>
    api.get(`/competitor-analysis/predict/${competitor}`, { params: { industry } }),
};

export const strategyService = {
  generateStrategy: (industry) =>
    api.get(`/industry/strategy/${industry}`),

  getNicheStrategy: (industry, niche) =>
    api.get(`/industry/niche-strategy`, { params: { industry, niche } }),

  optimizeContent: (industry, content) =>
    api.post(`/industry/optimize`, { industry, content }),
};

export const trendService = {
  getIndustryTrends: (industry) =>
    api.get(`/trends/industry/${industry}`),

  getRegionalTrends: (region) =>
    api.get(`/trends/region/${region}`),

  getPredictedTrends: (industry, timeframe) =>
    api.get('/trends/predicted', { params: { industry, timeframe } }),
};

export const MERMAID_CONFIG = {
  startOnLoad: false,
  securityLevel: 'strict',
  maxTextSize: 50_000,
  maxEdges: 500,
  htmlLabels: false,
  suppressErrorRendering: true,
  secure: ['secure', 'securityLevel', 'startOnLoad', 'maxTextSize', 'maxEdges', 'suppressErrorRendering', 'htmlLabels'],
} as const

import request from '@/request'

export interface AssetCriteria {
  orientation?: 'LANDSCAPE'
  dominantColorFamily?: 'BLUE_OR_PURPLE'
}

export interface AssetResult {
  schemaVersion: 1
  resultId: string
  spaceId: string
  observedAt: string
  criteria: AssetCriteria
  ruleVersion: string
  items: {
    position: number
    status: 'AVAILABLE' | 'STALE' | 'UNAVAILABLE'
    assetRef?: { kind: 'INTERNAL'; pictureId: string }
    sourceVersion?: string
    verifiedFacts?: {
      width?: number
      height?: number
      orientation?: string
      dominantColor?: string
    }
    matches?: { criterion: string; status: string; evidence: Record<string, unknown> }[]
    interpretations?: string[]
    display?: { contentRef: string }
  }[]
}

export function searchAssets(body: {
  spaceId: string
  threadId?: string
  criteria: AssetCriteria
  limit?: number
}) {
  return request<{ code: number; data?: AssetResult; message?: string }>('/agent/asset-search', {
    method: 'POST',
    data: body,
  })
}

export function readAssetResult(resultId: string) {
  return request<{ code: number; data?: AssetResult; message?: string }>(
    `/agent/asset-results/${encodeURIComponent(resultId)}`,
    { method: 'GET' },
  )
}

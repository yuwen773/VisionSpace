import request from '@/request'

/**
 * Agent 长期偏好的手写客户端。
 *
 * 后端接口（#42）尚未进入 OpenAPI 规范，先手写这一小模块；
 * 后续重新生成客户端时若已纳入规范，应以生成产物为准。
 */

export type PreferenceExplicit = {
  liked_styles?: string[]
  disliked_styles?: string[]
  liked_colors?: string[]
  notes?: string
}

export type PreferenceFeedbackEntry = {
  time?: number
  action?: string | null
  reason?: string | null
}

export type PreferenceView = {
  explicit?: PreferenceExplicit
  learned?: Record<string, unknown>
  feedbackHistory?: PreferenceFeedbackEntry[]
  autoLearn?: boolean
  preferenceVersion?: number
  dataEpoch?: number
}

type BaseResponsePreferenceView = {
  code?: number
  data?: PreferenceView
  message?: string
}

/** 查看当前用户的长期偏好 */
export async function getAgentPreference() {
  return request<BaseResponsePreferenceView>('/agent/preference', {
    method: 'GET',
  })
}

/** 显式修改偏好设置（用户直接编辑，优先于模型推断） */
export async function updateAgentPreference(explicit: PreferenceExplicit) {
  return request<BaseResponsePreferenceView>('/agent/preference/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: { explicit },
  })
}

/** 清除偏好数据（保留墓碑行，在途学习不能写回已清除内容） */
export async function clearAgentPreference() {
  return request<BaseResponsePreferenceView>('/agent/preference/clear', {
    method: 'POST',
  })
}

/** 设置自动学习开关（默认关闭：开启前不发生任何自动学习） */
export async function setAgentAutoLearn(enabled: boolean) {
  return request<BaseResponsePreferenceView>('/agent/preference/auto-learn', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: { enabled },
  })
}

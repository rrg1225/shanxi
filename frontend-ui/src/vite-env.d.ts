/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DEFAULT_TENANT_ID?: string
  readonly VITE_DEFAULT_OWNER_USER_ID?: string
  /** 可选：与 ai-gateway / 后端导师人设一致 */
  readonly VITE_MENTOR_SYSTEM_PROMPT?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}


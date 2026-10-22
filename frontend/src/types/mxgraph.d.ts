/**
 * Type declarations for mxGraph (loaded via CDN script tag, see index.html)
 */

interface MxGraphInstance {
  setEnabled(enabled: boolean): void
  setCellsEditable(editable: boolean): void
  getModel(): MxGraphModel
  refresh(): void
}

interface MxGraphModel {
  // model methods
  [key: string]: unknown
}

interface MxCodecInstance {
  decode(doc: Node | Document | XMLDocument, model: MxGraphModel): void
}

interface MxClient {
  run: (callback: () => void) => void
  VERSION: string
  IS_IE: boolean
  IS_NS: boolean
  IS_OP: boolean
  IS_FF: boolean
  IS_SF: boolean
  IS_CHROME: boolean
  IS_OT: boolean
}

interface MxGraphConstructor {
  new (container: HTMLElement): MxGraphInstance
}

interface MxCodecConstructor {
  new (): MxCodecInstance
  decode(doc: Node | Document | XMLDocument, model: MxGraphModel): void
}

interface MxUtils {
  parseXmlString(xml: string): XMLDocument
  getOuterHtml(node: Node): string
}

declare global {
  interface Window {
    mxClient?: MxClient
    mxGraph?: MxGraphConstructor
    mxCodec?: MxCodecConstructor
    mxUtils?: MxUtils
  }
}

export {}

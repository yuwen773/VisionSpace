/**
 * Type declarations for mxGraph (loaded via script tag)
 */

declare namespace window {
  const mxClient: {
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

  const mxGraph: {
    new (container: HTMLElement): mxGraphInstance
  }

  interface mxGraphInstance {
    setEnabled(enabled: boolean): void
    setCellsEditable(editable: boolean): void
    getModel(): mxGraphModel
    refresh(): void
  }

  interface mxGraphModel {
    // model methods
  }

  const mxCodec: {
    new (): mxCodecInstance
    decode(doc: Document | XMLDocument, model: mxGraphModel): void
  }

  interface mxCodecInstance {
    decode(doc: Document | XMLDocument, model: mxGraphModel): void
  }

  const mxUtils: {
    parseXmlString(xml: string): XMLDocument
    getOuterHtml(node: Node): string
  }
}

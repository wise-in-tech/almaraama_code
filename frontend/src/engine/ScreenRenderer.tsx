import { useTranslation } from 'react-i18next'
import { useLocalizedText } from '@/shared/i18n'
import { log } from '@/shared/log'
import { findBlock, type Registry } from './registry'
import type { BlockDefinition, ScreenDefinition } from './schema'

/**
 * Renders a validated screen definition with the components of the registry. A block whose type is not registered,
 * or whose definition does not match its schema, is refused: nothing from it is rendered or executed.
 */
export function ScreenRenderer({ definition, registry }: { definition: ScreenDefinition; registry: Registry }) {
  const { localize } = useLocalizedText()
  return (
    <div className="grid gap-4" data-screen={definition.screen} data-version={definition.version}>
      {definition.title && <h2 className="text-xl font-semibold">{localize(definition.title)}</h2>}
      {definition.blocks.map((block, index) => (
        <BlockSlot key={index} block={block} registry={registry} />
      ))}
    </div>
  )
}

/** Renders one block from the closed registry, or a refusal when its type is unknown or its definition invalid. */
function BlockSlot({ block, registry }: { block: BlockDefinition; registry: Registry }) {
  const registered = findBlock(registry, block.type)
  if (!registered) {
    return <RefusedBlock type={block.type} reason="not registered" />
  }
  const result = registered.render(block)
  if (!result.ok) {
    return <RefusedBlock type={block.type} reason={result.reason} />
  }
  return result.element
}

/** Visible placeholder for a refused block; nothing from its definition is executed or rendered as HTML. */
function RefusedBlock({ type, reason }: { type: string; reason: string }) {
  const { t } = useTranslation()
  if (import.meta.env.DEV) {
    log.warn('engine', `block "${type}" refused: ${reason}`)
  }
  return (
    <p role="note" data-refused-block={type} className="rounded-lg border border-dashed border-line p-3 text-sm text-muted">
      {t('engine.refused')}
    </p>
  )
}

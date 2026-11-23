import { fieldGroupBlock } from './blocks/FieldGroup'

export { ScreenRenderer } from './ScreenRenderer'
export { createRegistry, defineBlock, type Registry, type RegisteredBlock } from './registry'
export {
  localizedTextSchema,
  parseScreenDefinition,
  screenDefinitionSchema,
  type BlockDefinition,
  type ScreenDefinition,
} from './schema'

/** Blocks owned by the engine. Features add their business blocks with `createRegistry(coreBlocks, theirs)`. */
export const coreBlocks = { FieldGroup: fieldGroupBlock }

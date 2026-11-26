# Frontend almaraama

Application web et mobile unique : React, TypeScript, Vite, PWA. Décision : ADR-024. Document illustré :
`docs/architecture_frontend.html`.

## Contrat d'abord

Le contrat `../api/openapi.yaml` est la source unique. On l'écrit et on le valide **avant** de coder un écran ou un
point d'entrée ; ensuite le backend et le frontend avancent en parallèle.

1. Ajouter ou modifier le point d'entrée dans `api/openapi.yaml` (revue commune backend et frontend).
2. `pnpm gen:api` régénère les types (`src/shared/api/schema.d.ts`, jamais commité). Chaque `dev`, `build` et
   `typecheck` le fait aussi.
3. Tant que le backend n'a pas implémenté le point d'entrée, ajouter un mock dans `src/mocks/handlers.ts` et travailler
   avec `pnpm dev:mocks`.
4. Quand le backend l'implémente, supprimer le mock : `pnpm dev` passe par le proxy vers l'API réelle.

## Démarrer

Node 24 et pnpm sont nécessaires (`corepack enable` active pnpm).

```sh
pnpm install
pnpm dev          # http://localhost:5173, /api renvoyé vers le backend (localhost:8080)
pnpm dev:mocks    # même chose, avec les mocks du contrat pour les points d'entrée pas encore faits
pnpm verify       # textes, types, lint, tests, construction : ce que fait l'intégration continue
pnpm sync:texts   # après un changement des fichiers de traduction : copie pour le dictionnaire du backend (ADR-029)
```

Sans Node 24 en local, par Docker (les `node_modules` restent dans un volume Docker) :

```sh
docker run --rm -v "$PWD/..":/w -w /w/frontend \
  -v almaraama-pnpm-store:/pnpm-store -v almaraama-fe-node-modules:/w/frontend/node_modules \
  node:24-bookworm-slim sh -c 'corepack enable && pnpm install --store-dir /pnpm-store && pnpm verify'
```

## Structure

```
src/
├── app/          coquille, routeur, fournisseurs ; assemble les routes des fonctionnalités
├── features/     un dossier par module du backend, porte d'entrée unique index.ts
├── engine/       moteur d'écrans : schémas, registre fermé, rendu
├── shared/       client API généré, i18n (ar par défaut, en, fr), utilitaires, composants shadcn/ui
├── mocks/        mocks du contrat (pnpm dev:mocks uniquement)
└── test/         configuration des tests
```

Frontières vérifiées par ESLint (eslint-plugin-boundaries), comme ArchUnit côté backend :

| De | Peut importer |
| --- | --- |
| `app` | `engine`, `shared`, `mocks`, `features/*/index.ts` |
| `features/x` | ses propres fichiers, `engine`, `shared`, `features/*/index.ts` |
| `engine` | `shared` |
| `shared` | rien d'autre que lui-même |

## Règles

- **Sécurité :** jeton d'accès en mémoire seulement ; `dangerouslySetInnerHTML`, `innerHTML` et `eval` interdits par
  le lint ; aucun script en ligne (CSP stricte) ; l'interface masque, le serveur décide.
- **Langues :** tout texte passe par i18next (`src/shared/i18n/locales`). Utiliser les classes logiques de Tailwind
  (`ms-`, `me-`, `ps-`, `pe-`, `start-`, `end-`) pour que l'arabe fonctionne de droite à gauche.
- **Moteur d'écrans :** un bloc est déclaré avec `defineBlock(schéma Zod, composant)`. Un type absent du registre ou
  une définition invalide est refusé, rien n'est rendu.
- **Dépendances :** pnpm refuse toute version publiée depuis moins de 7 jours (`pnpm-workspace.yaml`).
  TypeScript reste en 6.0 tant que typescript-eslint ne prend pas en charge TypeScript 7.
- **Composants :** `pnpm dlx shadcn@latest add button` copie le composant dans `src/shared/ui`.

## Mode débogage (ADR-033)

Un seul interrupteur : `VITE_LOG_LEVEL` dans `frontend/.env.local` (`silent`, `warn`, `info`, `debug` ; `debug` par
défaut avec `pnpm dev`). Sur une version déjà déployée, un testeur l'active pour son seul navigateur avec
`localStorage["almaraama.log"] = "debug"` dans la console. Tous les journaux passent par `src/shared/log` (ESLint
interdit `console` ailleurs) : chaque appel d'API y écrit méthode, chemin, statut et `requestId`, jamais un corps, un
jeton ou une adresse e-mail complète. Le `requestId` permet de retrouver la requête dans les journaux du backend.

## Anti-robot (ADR-032)

Les formulaires publics qui envoient un e-mail (mot de passe oublié, nouveau lien de confirmation) utilisent
`AltchaField` (`src/shared/altcha`, composant ALTCHA 2.3.0). Le calcul démarre à l'affichage du formulaire ; le bouton
attend la preuve. Dans les tests, le module est remplacé par un bouchon qui donne une preuve tout de suite.

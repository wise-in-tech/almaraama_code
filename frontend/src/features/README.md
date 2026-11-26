# Fonctionnalités

Un dossier par module du backend (`maintenance`, `assets`, `sites`, `membership`, `identity`, `preventive`,
`notification`, `files`), créé avec le premier écran du module.

```
features/<module>/
├── routes/        écrans du module
├── components/
├── api/           hooks TanStack Query sur le client généré
└── index.ts       seule porte d'entrée publique : routes, blocs du moteur d'écrans
```

Règles vérifiées par ESLint (eslint-plugin-boundaries) :

- une fonctionnalité utilise `engine` et `shared` ;
- entre fonctionnalités, seul `index.ts` est importable ;
- `shared` et `engine` ne connaissent aucune fonctionnalité.

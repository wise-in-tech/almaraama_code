# Contrat d'API

`openapi.yaml` est la **source unique** de l'API publique d'almaraama (OpenAPI 3.1). Méthode de travail : le contrat
d'abord, puis le backend et le frontend en parallèle.

1. **Écrire le contrat** d'un point d'entrée : chemin, paramètres, corps, réponses, codes d'erreur. Noms en anglais
   (glossaire), règles de la spécification citées dans les descriptions.
2. **Le valider ensemble**, backend et frontend, dans la même demande de fusion.
3. **En parallèle :**
   - le frontend génère ses types (`pnpm gen:api`) et simule le point d'entrée avec MSW tant qu'il n'existe pas ;
   - le backend l'implémente et est testé contre ce contrat.
4. Le mock est supprimé quand le backend livre le point d'entrée.

Conventions :

- Préfixe `/api/v1/…`. Identifiants publics en UUID v7.
- Erreurs au format `Problem` (RFC 9457) avec un `code` stable en anglais, traduit par le frontend.
- Commandes modifiantes acceptant l'en-tête `Idempotency-Key`.
- DTO distincts selon le rôle quand des montants sont en jeu (J3).

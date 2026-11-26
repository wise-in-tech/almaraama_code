# Backend almaraama

Monolithe modulaire Java 25 / Spring Boot 4 / Spring Modulith : un module Maven par contexte délimité, architecture
hexagonale dans chaque module. Décisions : ADR-002, ADR-003, ADR-022, **ADR-023**.

## Structure

```
backend/
├── pom.xml                        parent : versions, plugins, nomenclature interne
├── platform/
│   ├── shared/                    noyau partagé (CF-9) : identifiants, erreurs ; Java pur
│   └── app/                       application déployable : main, sécurité, configuration,
│                                  migration "platform", tests transverses
└── modules/<contexte>/            identity, sites, membership, assets, maintenance, preventive,
    │                              workflow, customization, notification, audit, files
    ├── api/   (<contexte>-api)    contrat : com.almaraama.<contexte>.api et .api.events
    └── impl/  (<contexte>-impl)   com.almaraama.<contexte>.domain        Java pur (CF-8)
                                   com.almaraama.<contexte>.application   cas d'usage, un agrégat par transaction
                                   com.almaraama.<contexte>.adapters.in   REST, écouteurs, tâches
                                   com.almaraama.<contexte>.adapters.out  persistance, autres modules, systèmes externes
                                   resources/db/migration/<contexte>/     schéma et migrations du module
```

Règles de dépendance (vérifiées par le compilateur) :

- un `-api` ne dépend que de `shared` ;
- un `-impl` dépend de son `-api`, de `shared` et des `-api` des modules qu'il a le droit d'utiliser ;
- un `-impl` ne dépend jamais d'un autre `-impl` ; seul `app` les assemble.

Le domaine est en Java pur, avec les annotations JPA standard sur les agrégats. Les applications externes (SAP,
ServiceNow, SMS, stockage, IA…) sont toujours derrière un port défini par le métier et un adaptateur par technologie
(ADR-023, section 3).

## Démarrer

```sh
cp .env.example .env            # à la racine du dépôt ; mettre des mots de passe forts
mkdir -p secrets && openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/jwt-private.pem
chmod 644 secrets/jwt-private.pem
docker compose up -d --build    # PostgreSQL 17 + backend
```

Générer aussi la clé de l'anti-robot : `openssl rand -hex 32 > secrets/altcha-hmac.key && chmod 644
secrets/altcha-hmac.key` (ADR-032).

Pour essayer la connexion : dans `.env`, mettre `SPRING_PROFILES_ACTIVE=dev` et `ALMARAAMA_DEV_INVITE_EMAIL=moi@exemple.dz`.
Au démarrage, ce compte devient administrateur de la plateforme et promoteur d'une organisation de démonstration ; son
e-mail « définir mon mot de passe » arrive dans **Mailpit**, http://127.0.0.1:8025 (ADR-031). Ouvrir le lien avec
`pnpm dev` dans `frontend/`. Les données de référence de développement (quelques wilayas et communes, typologies de
biens) sont dans `platform/app/src/main/resources/db/dev`.

- API : http://127.0.0.1:8080/api. Tout demande un jeton d'accès, sauf la connexion, le mot de passe oublié, les liens
  de compte et les défis anti-robot (ADR-030, ADR-032).
- E-mails de développement : http://127.0.0.1:8025 (Mailpit, service `mailpit`).

### Mode débogage (ADR-033)

Un seul interrupteur : `ALMARAAMA_LOG_LEVEL` dans `.env` (`INFO` par défaut, `DEBUG`, `TRACE`) ; le profil `dev` est en
`DEBUG`. Il règle le groupe de journaux `almaraama` : notre code (entrée et résultat des cas d'utilisation, décisions de
droits), les requêtes SQL sans leurs valeurs, la boîte d'envoi des événements et Flyway. Chaque réponse porte un en-tête
`X-Request-Id`, repris dans le champ `requestId` des erreurs : `docker compose logs backend | grep <requestId>` retrouve
toute la requête. Mots de passe, secrets, jetons, e-mails complets et téléphones ne sont jamais journalisés.
- Santé et métriques : port 8081 dans le conteneur, jamais publié.

Pour lancer le backend depuis l'IDE : `docker compose up -d postgres`, puis exporter les variables de `.env` et
démarrer `AlmaraamaApplication` (module `platform/app`).

## Construire et tester

Avec un JDK 25 local :

```sh
./mvnw verify                                   # tout
./mvnw verify -pl modules/maintenance/impl -am  # un module et ce dont il dépend
```

Sans JDK 25 (Docker Desktop) :

```sh
docker run --rm -v "$PWD/..":/w -w /w/backend -v almaraama-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3.9-eclipse-temurin-25 mvn -B verify
```

| Test (module `app`, sauf mention) | Vérifie |
| --- | --- |
| `ModularityTests` | Graphe des modules, interfaces nommées `api` et `events`, dépendances autorisées (CF-1). Diagrammes dans `platform/app/target/spring-modulith-docs`. |
| `ArchitectureTests` | Domaine sans technologie (CF-8), cas d'usage sans technologie, contrats en Java pur (CF-1, CF-5), noyau partagé indépendant (CF-9), `Perimeter` passé en paramètre (CF-11). |
| `PlatformIntegrationTests` | Un schéma par module (CF-3), aucun `DELETE` pour le rôle applicatif sur toutes les tables futures (M2), API fermée et en-têtes de sécurité. |
| `IdentityFlowTests` | Lien « définir mon mot de passe », connexion par e-mail, vérification de l'e-mail, mot de passe oublié, verrouillage, anti-robot, limite d'envoi, renouvellement, réutilisation de jeton, CSRF, déconnexion, `requestId`, journal, aucun SMS (D1, D4, D5, M2, critère 7). |
| `Sprint1FlowTests` | Résidence dans une commune et adresse copiée aux bâtiments, filtres et zones provisoires, catégories de biens, résidents, un seul propriétaire, demandes d'accès, cloisonnement, droits par rôle et par personne, défauts changés en base (A1 à A9, C8 à C11, D2, D4, E4, critères 1, 2, 3, 13, 15 à 18). |
| `AuthRateLimitTests` | Limitation de débit sur la connexion. |
| Tests du domaine `identity` | Numéro de téléphone, règles du mot de passe, e-mail normalisé, liens à usage unique, verrouillage progressif. |
| `UuidV7Test` (`shared`) | Identifiants UUID v7 générés par l'application (CF-10). |

## Ajouter un contexte

1. Copier la structure d'un module existant dans `modules/<contexte>/` (trois `pom.xml`, `package-info.java` avec
   les dépendances autorisées).
2. Le déclarer dans le `pom.xml` racine (`<modules>` et nomenclature interne) et comme dépendance de `platform/app`.
3. Ajouter sa première migration : création du schéma et droits du rôle applicatif, sans `DELETE`.
4. Ajouter le schéma à la liste de `PlatformIntegrationTests`, et le contexte au glossaire s'il introduit un terme.

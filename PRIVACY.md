# Politique de confidentialité

Pellicule est une application personnelle, non distribuée publiquement (pas de présence sur le Play
Store) : ce document décrit les données traitées pour toute personne qui l'installe malgré tout à
partir des sources de ce dépôt.

## Données collectées

- **Compte Google** (via Google Sign-In et Supabase Auth) : identifiant de compte, adresse e-mail,
  nom et photo de profil éventuels. Sert uniquement à identifier l'utilisateur et à cloisonner ses
  données dans Supabase — aucun autre usage.
- **Suivi de films, séries et anime** : titres, types, statuts, années, notes personnelles et
  statuts d'épisode vu/non-vu saisis par l'utilisateur, ainsi que les métadonnées récupérées
  automatiquement (affiche, synopsis, épisodes) via l'API TMDB.
- **Requêtes de recherche** : les termes tapés dans l'écran Recherche sont envoyés à l'API TMDB pour
  renvoyer des résultats. Ils ne sont pas stockés au-delà de la session de recherche.
- **Connexion à un serveur Jellyfin (optionnelle)** : si l'utilisateur renseigne un serveur, l'URL,
  le nom d'utilisateur et le jeton d'accès obtenu après authentification sont stockés localement
  (chiffrés sur l'appareil), pour synchroniser le statut vu dans les deux sens. Le mot de passe
  Jellyfin n'est jamais stocké. Cette connexion est entièrement optionnelle : l'application
  fonctionne normalement sans elle.

Aucune autre donnée n'est demandée : pas de géolocalisation, pas de contacts, pas d'identifiant
publicitaire.

## Stockage et accès

- Le suivi est stocké localement sur l'appareil (base Room) et synchronisé sur Supabase
  (base Postgres hébergée dans l'Union européenne), où chaque ligne est rattachée à un compte. La
  sécurité par ligne (RLS) de la base empêche un utilisateur d'accéder aux données d'un autre, et
  les inscriptions sont limitées à une liste d'adresses autorisées.
- La session Jellyfin (URL, identifiant, jeton d'accès) est stockée exclusivement sur l'appareil, de
  façon chiffrée (`EncryptedSharedPreferences`) — jamais envoyée à Supabase ni à aucun autre tiers
  que le serveur Jellyfin renseigné par l'utilisateur.
- Aucune donnée locale n'est incluse dans les sauvegardes automatiques Android
  (`allowBackup="false"`).

## Partage avec des tiers

- **TMDB** reçoit les requêtes nécessaires à la recherche de contenus et à l'affichage des fiches
  (titre recherché, identifiant du contenu) — aucune donnée de compte n'est transmise.
- **Supabase** héberge l'authentification et les données du suivi ; **Google** fournit la connexion
  par compte Google.
- **Le serveur Jellyfin renseigné par l'utilisateur**, s'il en configure un : reçoit les identifiants
  de connexion saisis et les mises à jour de statut vu envoyées par l'application. Ce serveur est
  choisi et administré par l'utilisateur lui-même, hors du contrôle de l'application.
- Aucune vente, aucun partage à des fins publicitaires, aucun tracking tiers.

## Conservation et suppression

Les données sont conservées tant que le compte est utilisé. Pour une suppression complète (compte
Supabase et données associées), contacter l'adresse ci-dessous. La session Jellyfin peut
être supprimée à tout moment depuis l'écran Compte de l'application (déconnexion du serveur).

## Contact

Pour toute question sur cette politique ou pour demander la suppression de vos données :
contact@cklla.fr

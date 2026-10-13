# Restaurer une sauvegarde

Le job `sauvegarde_supabase` produit `sauvegarde.sql` (artefact du pipeline, conservé 30 jours) avec
`pg_dump --data-only --schema=public --no-owner`. Le dump contient les données des tables `media`,
`watched_episode` et `allowed_email`, pas les comptes : `auth.users` n'en fait volontairement pas partie
(adresses et données d'authentification hors d'un artefact CI).

Or `media.user_id` référence `auth.users`. Selon le cas, la restauration est directe ou demande un
remappage.

## Cas 1 : même projet Supabase, comptes inchangés

Les comptes existent avec les mêmes identifiants : le dump se rejoue tel quel.

1. Vider les tables si elles contiennent des données (éditeur SQL du tableau de bord) :
   `truncate public.media, public.watched_episode, public.allowed_email cascade;`
2. Rejouer le dump, en une transaction : `psql "$SUPABASE_DB_URL" --single-transaction -v ON_ERROR_STOP=1 -f sauvegarde.sql`

## Cas 2 : nouveau projet, ou comptes recréés (identifiants différents)

1. Appliquer les migrations de `supabase/migrations/` dans l'ordre (éditeur SQL), brancher le hook
   « Before User Created » et activer Google, comme pour le projet d'origine.
2. Ajouter à la main les adresses dans `allowed_email` (le hook refuse sinon la première connexion), puis
   se connecter une fois avec chaque compte : cela crée les lignes de `auth.users` avec de nouveaux
   identifiants.
3. Relever, pour chaque compte, l'ancien identifiant (colonne `user_id` du dump, ou `auth.users` de
   l'ancien projet s'il est encore lisible) et le nouveau (`select id, email from auth.users;`).
4. Remplacer chaque ancien identifiant par le nouveau dans le dump :
   `sed -e 's/<ancien-a>/<nouveau-a>/g' -e 's/<ancien-b>/<nouveau-b>/g' sauvegarde.sql > remappee.sql`
   Les identifiants de contenu (`media.id`) ne changent pas : ce sont des UUID générés par l'application.
5. Vider `allowed_email` (le dump la restaure aussi, ses lignes entreraient en conflit avec celles de
   l'étape 2) : `truncate public.allowed_email;`
6. Rejouer : `psql "$SUPABASE_DB_URL" --single-transaction -v ON_ERROR_STOP=1 -f remappee.sql`

Un dump rejoué sans remappage est refusé en bloc (violation de la clé étrangère vers `auth.users`),
et la transaction ne laisse rien derrière elle.

## Vérification

Comparer avant et après : `select count(*) from public.media;` et `select count(*) from public.watched_episode;`.

`supabase/tests/restaurer_sauvegarde.sh` rejoue cette procédure sur deux Postgres jetables (Docker) :
dump brut refusé, dump remappé restauré en entier.

## Après une restauration

Le serveur fait foi : au lancement suivant, le miroir supprime des appareils les contenus absents du
dump restauré (sauf ceux qui ont une écriture en attente). Restaurer une sauvegarde ancienne efface donc
sur les appareils ce qui a été ajouté depuis ; en cas de doute, garder une copie du dump choisi avant de
rejouer.

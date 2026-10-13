# Export Firestore → Supabase

Reprend le suivi d'un compte (`users/{uid}/movies` dans Firestore) pour l'importer dans Supabase, avec
les identifiants d'origine et les épisodes vus (`watchedEpisodes`, format `saison:épisode`).

**Lecture seule** : le script ne lit que cette collection et n'appelle que `stream()`. Il n'écrit et ne
supprime rien dans Firebase. Les données Firestore restent intactes.

## Préparation (une fois)

1. Console Google Cloud, projet `fir-oc-beddb` : créer un compte de service avec le seul rôle
   **Lecteur Cloud Datastore** et télécharger sa clé JSON (hors du dépôt, à supprimer après usage).
2. `python3 -m venv /tmp/export && /tmp/export/bin/pip install google-cloud-firestore`
3. `export GOOGLE_APPLICATION_CREDENTIALS=/chemin/vers/cle.json`
4. L'uid Firebase du compte à exporter : console Firebase > Authentication.
5. Le compte doit s'être connecté **une fois** à la nouvelle version de l'application, pour exister
   côté Supabase (le SQL retrouve son identifiant par son adresse).

## Export

```
/tmp/export/bin/python scripts/migration/export_firestore.py \
    --uid <uid Firebase> --email <adresse du compte Supabase> \
    --sortie /tmp/import-ami.sql
```

Le script affiche les comptages lus (documents, contenus, épisodes) et liste tout document écarté
(identifiant non UUID, valeur hors des bornes du schéma) ; il sort en erreur s'il en a écarté. Le
fichier SQL contient des données personnelles : le garder hors du dépôt et le supprimer ensuite.

## Import

Coller le SQL dans l'éditeur SQL du tableau de bord Supabase. Tout est dans une transaction :

- le compte est retrouvé par son adresse, l'import s'arrête s'il n'existe pas ;
- les lignes déjà présentes sont conservées (`on conflict do nothing`), le SQL se rejoue sans doublon ;
- l'import échoue, sans rien écrire, si un contenu ou un épisode lu dans Firestore n'existe pas ensuite
  pour ce compte (par exemple un identifiant déjà pris par un autre compte) ;
- le résultat final donne `medias_avant`, `medias_apres`, `medias_lus_dans_firestore` (et les mêmes
  pour les épisodes) : `apres` doit valoir au moins `lus`, et `avant + lus` quand le compte était vide.

## Tests

- `cd scripts/migration && python3 -m unittest -q` : conversion et lecture (sans Firebase ni réseau).
- `scripts/migration/tester_import.sh` : rejoue le SQL généré sur un Postgres jetable (Docker) avec le
  vrai schéma : import, rejeu, compte inconnu, identifiant pris par un autre compte.

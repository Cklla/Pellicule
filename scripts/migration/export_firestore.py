#!/usr/bin/env python3
"""Export en LECTURE SEULE de `users/{uid}/movies` depuis Firestore, converti en SQL pour Supabase.

Ne lit qu'une collection (celle du compte visé) et n'appelle jamais autre chose que `stream()` :
aucune écriture, aucune suppression dans Firebase. Pour que même une erreur ne puisse rien écrire,
utiliser un compte de service au rôle « Lecteur Cloud Datastore » (voir le README de ce dossier).

Usage :
    python3 export_firestore.py --uid <uid Firebase> --email <adresse du compte Supabase> \
        --sortie /chemin/hors/depot/import-ami.sql

Ou, sans accès à Firestore, depuis un export JSON déjà fait ({"<idDocument>": {champs...}, ...}) :
    python3 export_firestore.py --json documents.json --email ... --sortie ...
"""

import argparse
import json
import sys

from firestore_vers_sql import convertir, generer_sql


def lire_firestore(client, uid):
    """Lit les documents `users/{uid}/movies`. `client` n'a besoin que d'exposer `collection()`."""
    collection = client.collection("users").document(uid).collection("movies")
    return [(doc.id, doc.to_dict() or {}) for doc in collection.stream()]


def lire_json(chemin):
    with open(chemin, encoding="utf-8") as fichier:
        return list(json.load(fichier).items())


def rapport(resultat, nb_documents):
    lignes = [
        f"Documents lus dans Firestore : {nb_documents}",
        f"Contenus retenus              : {len(resultat.medias)}",
        f"Épisodes vus retenus          : {resultat.nb_episodes}",
        f"Contenus écartés              : {len(resultat.ecartes)}",
        f"Épisodes ignorés              : {len(resultat.episodes_ignores)}",
    ]
    for identifiant, raison in resultat.ecartes:
        lignes.append(f"  ÉCARTÉ {identifiant} : {raison}")
    for identifiant, raison in resultat.episodes_ignores:
        lignes.append(f"  IGNORÉ {identifiant} : {raison}")
    return "\n".join(lignes)


def main(argv=None):
    parseur = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    source = parseur.add_mutually_exclusive_group(required=True)
    source.add_argument("--uid", help="uid Firebase du compte dont on lit users/{uid}/movies")
    source.add_argument("--json", help="export JSON déjà fait, à la place de la lecture de Firestore")
    parseur.add_argument("--projet", default="fir-oc-beddb", help="identifiant du projet Firebase")
    parseur.add_argument("--email", required=True, help="adresse du compte Supabase qui recevra les données")
    parseur.add_argument("--sortie", required=True, help="fichier SQL à écrire (hors du dépôt : données personnelles)")
    args = parseur.parse_args(argv)

    if args.json:
        documents = lire_json(args.json)
    else:
        # Import tardif : seul ce mode a besoin de la bibliothèque et des identifiants Google.
        from google.cloud import firestore

        documents = lire_firestore(firestore.Client(project=args.projet), args.uid)

    resultat = convertir(documents)
    with open(args.sortie, "w", encoding="utf-8") as fichier:
        fichier.write(generer_sql(resultat, args.email))
    print(rapport(resultat, len(documents)))
    print(f"SQL écrit dans {args.sortie}")
    # Un document écarté est une donnée qui ne sera pas migrée : à regarder avant d'importer.
    return 1 if resultat.ecartes else 0


if __name__ == "__main__":
    sys.exit(main())

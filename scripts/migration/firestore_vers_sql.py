"""Conversion des documents Firestore `users/{uid}/movies/{id}` en SQL d'import pour Supabase.

Module pur (aucune dépendance, aucun accès réseau) : testable sans Firebase. La lecture de Firestore
est dans `export_firestore.py`.

Les bornes vérifiées ici reprennent les contraintes CHECK de `supabase/migrations`. Un document qui
n'y tient pas est écarté et signalé, jamais corrigé en silence.
"""

import uuid
from dataclasses import dataclass, field

TYPES = {"FILM", "SERIE", "ANIME"}
STATUTS = {"A_VOIR", "EN_COURS", "VU"}


@dataclass
class Resultat:
    """Ce qui a été retenu, et ce qui a été écarté (avec la raison)."""

    medias: list = field(default_factory=list)
    # {id_media: [(saison, episode), ...]} trié, sans doublon.
    episodes: dict = field(default_factory=dict)
    ecartes: list = field(default_factory=list)
    episodes_ignores: list = field(default_factory=list)

    @property
    def nb_episodes(self):
        return sum(len(v) for v in self.episodes.values())


def _entier(valeur, nom, minimum, maximum):
    """Renvoie (valeur, erreur). `None` est valide (champ facultatif)."""
    if valeur is None:
        return None, None
    # `bool` est un `int` en Python : un booléen n'est pas un entier valide ici.
    if isinstance(valeur, bool) or not isinstance(valeur, (int, float)) or int(valeur) != valeur:
        return None, f"{nom} n'est pas un entier ({valeur!r})"
    valeur = int(valeur)
    if not minimum <= valeur <= maximum:
        return None, f"{nom} hors bornes ({valeur})"
    return valeur, None


def _texte(valeur, nom, maximum, obligatoire=False):
    if valeur is None:
        return (None, f"{nom} absent") if obligatoire else (None, None)
    if not isinstance(valeur, str):
        return None, f"{nom} n'est pas un texte"
    if "\x00" in valeur:
        return None, f"{nom} contient un caractère nul"
    if obligatoire and len(valeur) == 0:
        return None, f"{nom} vide"
    if len(valeur) > maximum:
        return None, f"{nom} trop long ({len(valeur)} caractères)"
    return valeur, None


def convertir_media(id_document, donnees):
    """Renvoie (media, erreur) : `media` est un dict de colonnes SQL, `erreur` un texte ou `None`."""
    try:
        identifiant = str(uuid.UUID(id_document))
    except (ValueError, AttributeError, TypeError):
        return None, f"identifiant non UUID ({id_document!r})"

    titre, erreur = _texte(donnees.get("title"), "title", 300, obligatoire=True)
    if erreur:
        return None, erreur
    type_ = donnees.get("type")
    if type_ not in TYPES:
        return None, f"type inconnu ({type_!r})"
    statut = donnees.get("status")
    if statut not in STATUTS:
        return None, f"statut inconnu ({statut!r})"

    tmdb_id, erreur = _entier(donnees.get("tmdbId"), "tmdbId", 0, 9_999_999_999)
    if erreur:
        return None, erreur
    annee, erreur = _entier(donnees.get("releaseYear"), "releaseYear", 1850, 2999)
    if erreur:
        return None, erreur
    note, erreur = _entier(donnees.get("rating"), "rating", 1, 5)
    if erreur:
        return None, erreur
    vu_le, erreur = _entier(donnees.get("watchedAt"), "watchedAt", 0, 32_503_680_000_000)
    if erreur:
        return None, erreur
    affiche, erreur = _texte(donnees.get("posterUrl"), "posterUrl", 2000)
    if erreur:
        return None, erreur
    jellyfin, erreur = _texte(donnees.get("jellyfinId"), "jellyfinId", 200)
    if erreur:
        return None, erreur

    # Comme l'application, qui n'accepte que du HTTPS pour une image à charger.
    if affiche is not None and not affiche.startswith("https://"):
        affiche = None

    return {
        "id": identifiant,
        "title": titre,
        "type": type_,
        "status": statut,
        "tmdb_id": tmdb_id,
        "release_year": annee,
        "poster_url": affiche,
        "jellyfin_id": jellyfin,
        "rating": note,
        "watched_at": vu_le,
    }, None


def convertir_episode(brut):
    """Format `saison:épisode` (« 2:5 ») -> (saison, épisode), ou `None` si illisible ou hors bornes.

    Les deux numéros sont lus tels quels, jamais recombinés en un seul entier : un anime dépasse
    mille épisodes dans une même saison.
    """
    if not isinstance(brut, str):
        return None
    morceaux = brut.split(":")
    if len(morceaux) != 2 or not all(m.isascii() and m.isdigit() for m in morceaux):
        return None
    saison, episode = int(morceaux[0]), int(morceaux[1])
    if not (0 <= saison <= 9999 and 0 <= episode <= 99999):
        return None
    return saison, episode


def convertir(documents):
    """`documents` : itérable de (id_document, dict de champs). Renvoie un `Resultat`."""
    resultat = Resultat()
    vus = set()
    for id_document, donnees in sorted(documents, key=lambda d: str(d[0])):
        media, erreur = convertir_media(id_document, donnees)
        if erreur:
            resultat.ecartes.append((id_document, erreur))
            continue
        if media["id"] in vus:
            resultat.ecartes.append((id_document, "identifiant en double"))
            continue
        vus.add(media["id"])
        resultat.medias.append(media)

        liste = donnees.get("watchedEpisodes")
        if liste is None:
            continue
        if not isinstance(liste, list):
            resultat.episodes_ignores.append((id_document, f"watchedEpisodes n'est pas une liste ({liste!r})"))
            continue
        retenus = set()
        for brut in liste:
            cle = convertir_episode(brut)
            if cle is None:
                resultat.episodes_ignores.append((id_document, f"épisode illisible ({brut!r})"))
            else:
                retenus.add(cle)
        if retenus:
            resultat.episodes[media["id"]] = sorted(retenus)
    return resultat


def _litteral(valeur):
    if valeur is None:
        return "null"
    if isinstance(valeur, int):
        return str(valeur)
    return "'" + valeur.replace("'", "''") + "'"


COLONNES = ["id", "title", "type", "status", "tmdb_id", "release_year", "poster_url", "jellyfin_id", "rating", "watched_at"]
# Types explicites : dans un `values`, une colonne entièrement nulle serait typée `text` et ferait
# échouer l'insertion.
TYPES_SQL = {
    "id": "uuid", "title": "text", "type": "text", "status": "text", "tmdb_id": "bigint",
    "release_year": "integer", "poster_url": "text", "jellyfin_id": "text", "rating": "integer",
    "watched_at": "bigint",
}


def generer_sql(resultat, email):
    """SQL d'import pour le compte Supabase d'adresse `email`, rejouable sans doublon.

    À exécuter dans l'éditeur SQL du tableau de bord (rôle `postgres`, qui contourne le RLS). Le compte
    doit s'être connecté une fois à l'application pour exister dans `auth.users`. Tout est dans une
    transaction : si un comptage final ne correspond pas, rien n'est importé.
    """
    medias = resultat.medias
    attendus_episodes = resultat.nb_episodes
    lignes = [
        "-- Import ponctuel du suivi depuis Firestore. Rejouable : les lignes déjà présentes sont conservées.",
        "begin;",
        "",
        "create temp table _import_compte on commit drop as",
        f"    select id from auth.users where lower(email) = lower({_litteral(email)});",
        "",
        "do $$ begin",
        "    if (select count(*) from _import_compte) <> 1 then",
        "        raise exception 'Aucun compte (ou plusieurs) pour cette adresse : le compte s''est-il déjà connecté à l''application ?';",
        "    end if;",
        "end $$;",
        "",
        "create temp table _import_avant on commit drop as",
        "    select",
        "        (select count(*) from public.media where user_id = (select id from _import_compte)) as medias,",
        "        (select count(*) from public.watched_episode where user_id = (select id from _import_compte)) as episodes;",
        "",
    ]

    if medias:
        valeurs = ",\n".join(
            "    (" + ", ".join(
                f"{_litteral(m[c])}::{TYPES_SQL[c]}" for c in COLONNES
            ) + ")"
            for m in medias
        )
        lignes += [
            f"insert into public.media (user_id, {', '.join(COLONNES)})",
            f"select u.id, v.* from (values\n{valeurs}\n) as v({', '.join(COLONNES)})",
            "cross join _import_compte u",
            "on conflict (id) do nothing;",
            "",
        ]

    episodes = [(mid, s, e) for mid, cles in sorted(resultat.episodes.items()) for (s, e) in cles]
    if episodes:
        valeurs = ",\n".join(f"    ('{mid}'::uuid, {s}, {e})" for mid, s, e in episodes)
        lignes += [
            "insert into public.watched_episode (user_id, media_id, season, episode)",
            f"select u.id, v.* from (values\n{valeurs}\n) as v(media_id, season, episode)",
            "cross join _import_compte u",
            # Un épisode dont le contenu appartient déjà à un autre compte serait refusé par la clé
            # étrangère composite : on ne l'insère que pour un contenu de ce compte.
            "where exists (select 1 from public.media m where m.id = v.media_id and m.user_id = u.id)",
            "on conflict do nothing;",
            "",
        ]

    ids = ", ".join(f"'{m['id']}'" for m in medias) or "null"
    lignes += [
        "-- Chaque contenu et chaque épisode lus dans Firestore doivent exister pour ce compte.",
        "do $$ declare manquants_medias int; manquants_episodes int; begin",
        f"    select count(*) into manquants_medias from unnest(array[{ids}]::uuid[]) as attendu(id)",
        "        where not exists (select 1 from public.media m where m.id = attendu.id and m.user_id = (select id from _import_compte));",
    ]
    if episodes:
        lignes += [
            "    select count(*) into manquants_episodes from (values",
            ",\n".join(f"        ('{mid}'::uuid, {s}, {e})" for mid, s, e in episodes),
            "    ) as attendu(media_id, season, episode)",
            "        where not exists (select 1 from public.watched_episode w where w.media_id = attendu.media_id",
            "            and w.season = attendu.season and w.episode = attendu.episode and w.user_id = (select id from _import_compte));",
        ]
    else:
        lignes.append("    manquants_episodes := 0;")
    lignes += [
        "    if manquants_medias > 0 or manquants_episodes > 0 then",
        "        raise exception 'Import incomplet : % contenus et % épisodes manquants (identifiant déjà pris par un autre compte ?)', manquants_medias, manquants_episodes;",
        "    end if;",
        "end $$;",
        "",
        "-- Comptages à comparer avec ceux affichés par le script d'export.",
        "select",
        "    (select medias from _import_avant) as medias_avant,",
        "    (select count(*) from public.media where user_id = (select id from _import_compte)) as medias_apres,",
        f"    {len(medias)} as medias_lus_dans_firestore,",
        "    (select episodes from _import_avant) as episodes_avant,",
        "    (select count(*) from public.watched_episode where user_id = (select id from _import_compte)) as episodes_apres,",
        f"    {attendus_episodes} as episodes_lus_dans_firestore;",
        "",
        "commit;",
        "",
    ]
    return "\n".join(lignes)

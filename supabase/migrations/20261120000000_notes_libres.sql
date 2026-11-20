-- Notes libres d'un contenu : colonne `notes` de `public.media`.
--
-- Texte saisi par l'utilisateur sur la fiche d'un film, d'une série ou d'un anime (impressions, où il
-- s'est arrêté…). Vide par défaut, jamais nul : l'absence de notes est une chaîne vide, comme dans
-- l'application. Borne de 5000 caractères, reprise à la saisie côté application.
--
-- Cette migration n'ajoute que la colonne et sa contrainte : ni politique RLS, ni colonne existante.
-- Aucun `grant` : ceux de `20261011000000_droits_tables.sql` portent sur la table entière
-- (`select, insert, update, delete` pour `authenticated`) et couvrent donc déjà la nouvelle colonne ;
-- `anon` n'a toujours aucun accès.
--
-- Sûre pour les versions déjà installées de l'application :
--   - la colonne a une valeur par défaut (`''`), donc les lignes existantes n'ont simplement pas de notes ;
--   - un client qui ne la connaît pas ne l'envoie pas, et ses écritures la laissent intacte (un upsert
--     PostgREST ne met à jour que les colonnes présentes dans le corps de la requête) ;
--   - un client qui ne la connaît pas ignore la colonne en lecture.
--
-- À appliquer une seule fois, à la main, dans l'éditeur SQL du tableau de bord, après une sauvegarde et
-- AVANT de publier une version de l'application qui envoie ce champ : sans la colonne, l'envoi du contenu
-- échoue (colonne inconnue) et reste en attente. Un rejeu échoue (la colonne existe déjà) sans rien modifier.
--
-- Retour arrière (à lancer à la main ; efface toutes les notes déjà enregistrées) :
--   alter table public.media drop column notes;

alter table public.media
    add column notes text not null default '' check (char_length(notes) <= 5000);

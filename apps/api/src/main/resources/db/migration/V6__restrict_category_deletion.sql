ALTER TABLE article DROP CONSTRAINT article_category_fk;
ALTER TABLE article
    ADD CONSTRAINT article_category_fk FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT;

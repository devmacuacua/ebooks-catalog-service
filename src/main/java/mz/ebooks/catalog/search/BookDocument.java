package mz.ebooks.catalog.search;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Document(indexName = "books")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword)
    private String coverImage;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Text, name = "authorNames")
    private List<String> authorNames;

    @Field(type = FieldType.Keyword, name = "categoryNames")
    private List<String> categoryNames;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String isbn;

    @Field(type = FieldType.Keyword)
    private String publisher;

    @Field(type = FieldType.Keyword)
    private String language;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Boolean, name = "subscriptionOnly")
    private boolean subscriptionOnly;

    @Field(type = FieldType.Boolean, name = "isActive")
    private boolean isActive;

    @Field(type = FieldType.Boolean, name = "isFeatured")
    private boolean isFeatured;

    @Field(type = FieldType.Double, name = "averageRating")
    private double averageRating;

    @Field(type = FieldType.Date, name = "publishedAt")
    private LocalDateTime publishedAt;
}

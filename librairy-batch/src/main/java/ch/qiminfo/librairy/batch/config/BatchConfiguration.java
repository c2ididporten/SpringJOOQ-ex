package ch.qiminfo.librairy.batch.config;

import ch.qiminfo.librairy.batch.processor.AuthorProcessor;
import ch.qiminfo.librairy.batch.processor.FilterAuthorProcessor;
import ch.qiminfo.librairy.batch.processor.bean.AuthorBean;
import ch.qiminfo.librairy.batch.processor.bean.AuthorCsv;
import com.google.common.collect.Lists;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.UTF_8;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

    @Value("classpath:sql/insert-into-author.sql")
    private Resource insertAuthorSQL;

    @Bean
    public FlatFileItemReader<AuthorCsv> reader() {
        BeanWrapperFieldSetMapper<AuthorCsv> authorCsvBeanWrapperFieldSetMapper = new BeanWrapperFieldSetMapper<>();
        authorCsvBeanWrapperFieldSetMapper.setTargetType(AuthorCsv.class);
        return new FlatFileItemReaderBuilder<AuthorCsv>()
                .name("personItemReader")
                .resource(new ClassPathResource("sample-data-author.csv"))
                .delimited()
                .names("firstName", "lastName", "externalUid")
                .fieldSetMapper(authorCsvBeanWrapperFieldSetMapper)
                .build();
    }

    @Bean
    public ItemProcessor<AuthorCsv, AuthorBean> compositeAuthorProcessor(AuthorProcessor authorProcessor,
                                                                         FilterAuthorProcessor filterAuthorProcessor) {
        CompositeItemProcessor<AuthorCsv, AuthorBean> processor = new CompositeItemProcessor<>();
        processor.setDelegates(Lists.newArrayList(authorProcessor, filterAuthorProcessor));
        return processor;
    }

    @Bean
    public JdbcBatchItemWriter<AuthorBean> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AuthorBean>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql(asString(this.insertAuthorSQL))
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public Step step1(StepBuilderFactory stepBuilderFactory,
                      ItemProcessor<AuthorCsv, AuthorBean> compositeAuthorProcessor,
                      ItemWriter<AuthorBean> writer) {
        return stepBuilderFactory.get("step1")
                .<AuthorCsv, AuthorBean>chunk(10)
                .reader(reader())
                .processor(compositeAuthorProcessor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job importAuthorJob(JobBuilderFactory jobBuilderFactory,
                               JobCompletionNotificationListener listener,
                               Step step1) {
        return jobBuilderFactory.get("importAuthorJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step1)
                .end()
                .build();
    }

    private static String asString(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
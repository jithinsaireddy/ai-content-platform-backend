package com.jithin.ai_content_platform.config;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Properties;

@Configuration
public class NLPConfig {

    @Value("${nlp.model.path}")
    private String modelPath;

    @Bean
    @Primary
    public StanfordCoreNLP enhancedNLPPipeline() {
        Properties props = new Properties();
        
        // Enhanced annotator configuration
        props.setProperty("annotators", 
            "tokenize,ssplit,pos,lemma,ner,parse,sentiment,coref,quote");
        
        // Tokenization options
        props.setProperty("tokenize.options", "untokenizable=noneKeep");
        
        // Sentence splitting options
        props.setProperty("ssplit.newlineIsSentenceBreak", "two");
        
        // POS tagger options
        props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
        
        // NER options
        props.setProperty("ner.useSUTime", "true");
        props.setProperty("ner.applyNumericClassifiers", "true");
        props.setProperty("ner.buildEntityMentions", "true");
        
        // Parser options
        props.setProperty("parse.model", "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
        props.setProperty("parse.maxlen", "100");
        
        // Sentiment analysis options
        props.setProperty("sentiment.model", modelPath);
        
        // Coreference options
        props.setProperty("coref.algorithm", "neural");
        
        // Thread optimization
        props.setProperty("threads", "4");
        
        return new StanfordCoreNLP(props);
    }
}

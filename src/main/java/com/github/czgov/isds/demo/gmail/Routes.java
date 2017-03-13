package com.github.czgov.isds.demo.gmail;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

import com.github.czgov.isds.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jludvice on 31.1.17.
 */
public class Routes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("isds:messages?environment={{isds.env}}&username={{isds.login}}&password={{isds.password}}&consumer.delay={{isds.delay}}")

                .idempotentConsumer(header(Constants.MSG_ID), MemoryIdempotentRepository.memoryIdempotentRepository())
                .log("new message: ${header.isds.subject} from ${header.isds.from}")
                .setHeader("subject").header(Constants.MSG_SUBJECT)
                .setBody().constant("E-mail sent with Sample application using camel-isds components. See attached files.")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        for (String name : exchange.getIn().getAttachmentNames()) {
                            log.info("attaachment name {}", name);
                            log.info("content type: {}", exchange.getIn().getAttachment(name).getContentType());
                        }
                    }
                })
                // prepare data for mustache template
                // dot has special usage in mustache -> it can't  be in header name
                .process(e -> {
                    Map<String, Object> h = new HashMap<>();
                    for (Map.Entry<String, Object> en : e.getIn().getHeaders().entrySet()) {
                        h.put(en.getKey().replace(".", "-"), en.getValue());
                    }
                    e.getIn().getHeaders().putAll(h);
                    // need to pass entryset so we can iterate over Map entries in mustache
                    e.getIn().setHeader("attachments", e.getIn().getAttachments().entrySet());

                })
                .to("mustache:/mail-template.mustache")
                .setHeader(Exchange.CONTENT_TYPE).constant("text/html")
                .marshal().mimeMultipart()

                .to("smtps://smtp.gmail.com?username={{gmail.login}}" +
                        "&from={{gmail.login}}" +
                        "&to={{gmail.recipient}}" +
                        "&password={{gmail.password}}")
                .log("email sent");
    }
}

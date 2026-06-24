
# Understanding Twitter V2 API Flow

@Component
@ConditionalOnExpression("${twitter-to-kafka-service.enable-v2-tweets} && not ${twitter-to-kafka-service.enable-mock-tweets}")
public class TwitterV2KafkaStreamRunner implements StreamRunner {

public void start()
{
	try {
		  twitterV2StreamHelper.setUpRulesModified(bearerToken , () -> rulesToBeCreated);
          twitterV2StreamHelper.setupRulesModified1RefactoredHOF(bearerToken, () -> rulesToBeCreated);
		  twitterV2StreamHelper.connect(bearerToken);
	}
	   catch (IOException | URISyntaxException | JSONException e) {
          LOG.error("Error streaming tweets in V2 API!", e);
          throw new RuntimeException("Error streaming tweets!", e);
         }
}

## The method setupRulesModified1RefactoredHOF(...) is "HOF, since it takes @Functional interface Supplier as input argument".

Invoking this HOF ->  
            twitterV2StreamHelper.setupRulesModified1RefactoredHOF(bearerToken, () -> rulesToBeCreated);

## setupRulesModified1RefactoredHOF((String bearerToken , Supplier<Map<String,String> getRulesMap)
│
├─► [STEP 1] fetchRulesJsonHandleNull(bearerToken) 
│              │
│              └─►  HTTP GET Request → Twitter V2 Rules Endpoint (Fetches existing rules from the Remote Service)
│                   ## Returns: Optional<List<String>> Optional.empty() Or Optional.of(List of Rule IDs)
│         ->  Optional.empty() if no rules exist, or Optional.of(List of Rule IDs) if rules exist
│                           │
│                           ├── Optional is NON-EMPTY  ──────────────────────────────┐
│                           └── Optional is EMPTY  ──► action.run()                  │
│                                                        └─► LOG.error(...)          │
│                                                                                    │
├─► [STEP 2] deleteExistingRulesV2APIHOF(bearerToken)                                │
│              │                                                                     │
│              └─► CALLED EAGERLY — executes immediately                             │
│                  Returns: Consumer<List<String>>  ◄── this is the "callback"       │
│                           (bearerToken captured as CLOSURE)                        │
│                                                                                    │ 
│    Optional.of(List of Rule IDs)  =>  (List<String>)                               │                                              │
├─► [STEP 3] Optional.ifPresentOrElse(Consumer consumer, Runnable action)  ◄─────────┘
│                        │
│                        └─► ## When Optional is NON-EMPTY , JDK internally calls:

│                    consumer.accept(rulesList)  i.e accepts the List<String> of Rule IDs as input 
│                        │
│                        └─►  deleteExistingRulesV2APIHOF(bearerToken).accept(rulesList)
│                                │
│                                └─► deleteRules(bearerToken, rulesList)
│                                        │
│                                        └─► HTTP POST → Twitter V2 Rules Endpoint
│                                            (deletes existing rules)
│
└─► [STEP 4] createRules(bearerToken, getRulesMap.get())
│
└─► HTTP POST → Twitter V2 Rules Endpoint
    (creates new rules from config)

Consumer<List<String>> deleteExistingRulesV2APIHOF(String bearerToken)
{
return rulesList -> {
	try {
	   deleteRules(bearerToken, rulesList);
	   } catch (URISyntaxException e) {
	     throw new RuntimeException(e);
	  } catch (IOException e) {
	  throw new RuntimeException(e);
	  }
	 };
	 }




## Key Distinction to Note

```
deleteExistingRulesV2APIHOF(bearerToken)   ← called EAGERLY at Step 2
just returns the Consumer object

consumer.accept(rulesList)                 ← called LAZILY at Step 3
only when Optional is non-empty
triggered by ifPresentOrElse() internally
```

The **method** runs at Step 2 (to produce the Consumer), but the **lambda body inside it** runs at
 Step 3 (when `accept()` is triggered by the JDK). This is the essence of deferred/lazy execution in
  functional programming.
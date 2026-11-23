package org.ivanivorontsov.stroka.ai

import org.json.JSONObject

data class AiChatProvider(val id: String, val name: String, val url: String) {
    val isWeb: Boolean get() = url.isNotBlank()
}

object AiWebChat {
    val providers = listOf(
        AiChatProvider("deepseek", "DeepSeek", "https://chat.deepseek.com/"),
        AiChatProvider("qwen", "Qwen", "https://chat.qwen.ai/"),
        AiChatProvider("chatgpt", "ChatGPT", "https://chatgpt.com/"),
        AiChatProvider("gemini", "Gemini", "https://gemini.google.com/app"),
        AiChatProvider("grok", "Grok", "https://grok.com/"),
        AiChatProvider("alice", "Алиса", "https://alice.yandex.ru/"),
        AiChatProvider("copilot", "Copilot", "https://copilot.microsoft.com/")
    )

    fun injectScript(prompt: String): String {
        val p = JSONObject.quote(prompt)
        return """(function(){
          var p=$p, sels=['#prompt-textarea','div.ProseMirror[contenteditable=true]','textarea#chat-input','#chat-input','rich-textarea .ql-editor','div.ql-editor[contenteditable=true]','textarea[placeholder]','textarea','[contenteditable=true][role=textbox]','[contenteditable=true]'];
          function vis(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
          var el=null;for(var i=0;i<sels.length&&!el;i++){var a=document.querySelectorAll(sels[i]);for(var j=0;j<a.length;j++)if(vis(a[j])){el=a[j];break;}}
          if(!el)return 'NO_INPUT';
          window.__strokaBefore=document.querySelectorAll('[data-message-author-role=assistant],.ds-markdown,.response-message-content,.model-response-text,.message-bubble').length;
          window.__strokaPrompt=p;el.focus();
          if(el.tagName==='TEXTAREA'||el.tagName==='INPUT'){var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(el,p);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}
          else {try{var s=getSelection(),r=document.createRange();r.selectNodeContents(el);s.removeAllRanges();s.addRange(r);document.execCommand('insertText',false,p);}catch(e){} if((el.innerText||'').indexOf(p.slice(0,16))<0){el.textContent=p;el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));}}
          return 'OK';
        })()"""
    }

    fun sendScript(): String = """(function(){
      var sels=['button[data-testid=send-button]','#composer-submit-button','#send-message-button','button[aria-label*=Send i]','button[aria-label*=Отправ i]','button[aria-label*=Submit i]','button.send-button','button[type=submit]','[role=button][aria-label*=Send i]','[role=button][aria-label*=Отправ i]'];
      function vis(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
      for(var i=0;i<sels.length;i++){var a=document.querySelectorAll(sels[i]);for(var j=0;j<a.length;j++)if(vis(a[j])&&!a[j].disabled&&a[j].getAttribute('aria-disabled')!=='true'){a[j].click();return 'SENT';}}
      var e=document.querySelector('#prompt-textarea,textarea#chat-input,textarea,[contenteditable=true]');if(e){e.focus();e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));e.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));return 'ENTER';}
      return 'NO_INPUT';
    })()"""

    fun pollScript(provider: String): String {
        val selector = when(provider) {
            "deepseek" -> ".ds-markdown"
            "qwen" -> ".response-message-content.phase-answer,.response-message-content,.qwen-markdown"
            "chatgpt" -> "[data-message-author-role=assistant] .markdown,[data-message-author-role=assistant]"
            "gemini" -> "message-content,.model-response-text,.response-content"
            "grok" -> ".message-bubble"
            "alice" -> "[class*=markdown],[class*=message_assistant]"
            "copilot" -> "[class*=markdown],[class*=response]"
            else -> ".markdown,[class*=markdown]"
        }
        val sel = JSONObject.quote(selector)
        return """(function(){
          var s=$sel, a=Array.prototype.slice.call(document.querySelectorAll(s)).filter(function(e){var r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>0&&r.height>0&&c.display!=='none'&&c.visibility!=='hidden';});
          var e=a.length?a[a.length-1]:null, text=e?(e.innerText||e.textContent||'').trim():'';
          var busy=!!document.querySelector('button[data-testid=stop-button],.stop-generating,[data-is-streaming=true],[class*=typing-cursor],[class*=is-streaming]');
          var login=!!document.querySelector('input[type=password]')||!document.querySelector('#prompt-textarea,textarea#chat-input,textarea,[contenteditable=true]');
          return JSON.stringify({text:text,busy:busy,login:login,count:a.length,before:window.__strokaBefore||0,fresh:a.length>(window.__strokaBefore||0)&&text!==window.__strokaPrompt});
        })()"""
    }

    fun isLoginState(raw: String?): Boolean {
        val value = raw?.trim()?.trim('"') ?: return true
        return value == "NO_INPUT" || value == "NOINPUT"
    }
}

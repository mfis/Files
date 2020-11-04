var confirmIds = new Array();
var eventsource = null;
var activeRequest = false;
var breakScripts = false;
var navPointer = -1;
var navId = null;
var ajaxDone = new Array();
var refreshClass = 'figureicon pointer refresh';
var processed = '';
var monitor = false;
var clientpassword = '';
var clientpasswordCompare = '';
var isFileContentToViewCrypted = false;
var sendFileContent = false;
var elementNameToSendCrypted = '';
var objectURLs = new Array();

function initKeys() {
	var ke = document.getElementsByClassName('keyEvents');
	if (ke != null && ke.length > 0) {
		document.onkeydown = function(event) {
			if (event.keyCode < 48 || event.keyCode > 90) {
				var name = 'press' + event.keyCode;
				if (document.getElementById(name) != null) {
					event.cancelBubble = true;
					event.returnValue = false;
					execThis(document.getElementById(name).value);
				}
			}
			return event.returnValue;
		};
	}
}

function condSubmit(condition, index, submitOrAjax) {
	navPointer = -1;
	navId = null;
	breakScripts = true;
	activeRequest = true;
	ajaxDone = null;
	processed = '';
	monitor = false;
	for (var i = 0; i < objectURLs.length; i++) {
		window.URL.revokeObjectURL(objectURLs[i]);
	}
	objectURLs = new Array();
	if (eventsource != null) {
		eventsource.close();
	}
	document.getElementById('condition').value = condition;
	document.getElementById('requestType').value = submitOrAjax;

	if (index > -1) {
		document.getElementById('tableIndex').value = index;
	}
	Spinner.On();
	if (submitOrAjax == 'a') {
		Post.Send(document.getElementById('id_mainform'));
	} else {
		document.getElementById('id_mainform').submit();
	}
	activeRequest = false;
}

function display() {
	Post.Display(document.getElementById('id_mainform'));
	var doc = window.open().document;
	var html = "<html>" + document.getElementsByTagName('html')[0].innerHTML
			+ "</html>";
	doc.write("<textarea name='doc0' cols='180' rows='40'>" + html
			+ "</textarea>");
}

function rowNavi(cmd, max) {
	if (cmd == 'up' || cmd == 'down') {
		if (navId != null) {
			document.getElementById(navId).style.textDecoration = "none";
		}
		if (cmd == 'up') {
			if (navPointer > 0) {
				navPointer = navPointer - 1;
			} else {
				navPointer = max;
			}
		}
		if (cmd == 'down') {
			if (navPointer < max) {
				navPointer = navPointer + 1;
			} else {
				navPointer = 0;
			}
		}
		var id = 'nav' + navPointer;
		document.getElementById(id).style.textDecoration = "underline";
		navId = id;
	}
	if ((cmd == 'left' || cmd == 'right')) {
		var arr = document.getElementsByTagName('a');
		if (arr != null && arr.length > 0) {
			var next = false;
			for (var i = 0; i < arr.length; i++) {
				var j = (cmd == 'right') ? i : arr.length - i - 1;
				if (arr[j].id.indexOf('nav') == 0) {
					if (navId == null) {
						navId = arr[j].id;
						document.getElementById(navId).style.textDecoration = "underline";
						break;
					} else if (navId == arr[j].id) {
						next = true;
					} else if (next == true) {
						document.getElementById(navId).style.textDecoration = "none";
						navId = arr[j].id;
						document.getElementById(navId).style.textDecoration = "underline";
						break;
					}
				}
			}
		}
	}
	if (cmd == 'exe') {
		if (navId != null) {
			var navTemp = document.getElementById(navId);
			var cmd = navTemp.getAttribute('onclick');
			var iter = 0;
			while (cmd == null && iter < 20) {
				iter = iter + 1;
				navTemp = navTemp.parentNode;
				cmd = navTemp.getAttribute('onclick');
			}
			execThis(cmd);
		}
	}
}

function confirm(id, condition, index, submitOrAjax) {
	if (confirmIds[id] == 'toConfirm') {
		confirmIds[id] = 'done';
		condSubmit(condition, index, submitOrAjax);
	} else {
		confirmIds[id] = 'toConfirm';
		var innerhtml = document.getElementById(id).innerHTML;
		document.getElementById(id).innerHTML = innerhtml + ' ??';
		document.getElementById(id).style.color = "#FF0000";
	}
}

function setHidden(id) {
	document.getElementById(id).style.visibility = 'hidden';
}

function putStandaloneMarkerToElement() {
	if (("standalone" in window.navigator) && window.navigator.standalone) {
		document.getElementById('runAsWebApp').value = "true";
	} else {
		document.getElementById('runAsWebApp').value = "false";
	}
}

function initPushForTextView() {
	if (typeof (EventSource) !== "undefined") {
		var dest = document.getElementById('convID').value;
		eventsource = new EventSource(dest);
		eventsource.addEventListener('open', function(e) {
		}, false);
		eventsource
				.addEventListener(
						'message',
						function(e) {
							if (activeRequest == false) {
								if (e.data == 'refresh') {
									condSubmit('FS_VIEW_FILE', '-1', 'a');
								} else {
									document.getElementById('status_indicator').innerHTML = "<a class='dark'>&nbsp;&nbsp;"
											+ e.data + '</a>';
								}
							}
						}, false);
	}
}

function focusTextField(id) {
	self.focus();
	document.getElementById(id).focus();
}

function loadJS(name) {
	var script = document.createElement('script');
	script.setAttribute('type', 'text/javascript');
	script.src = name;
	document.getElementById("idhiddenfields").appendChild(script);
}

function fillIn() {
	if (ajaxDone == null) {
		return;
	}
	var elems = document.getElementsByClassName("ajaxFillIn");
	var i = 0;
	var weiter = true;
	while (i < elems.length && weiter == true) {
		if (ajaxDone[elems[i].id] == null) {
			var x = elems[i].id;
			Post.SendFillIn(x);
			ajaxDone[x] = x;
			weiter = false;
		}
		i++;
	}
	if (weiter == true) {
		elems = document.getElementsByClassName("ajaxFillInLow");
		i = 0;
		while (i < elems.length && weiter == true) {
			if (ajaxDone[elems[i].id] == null) {
				var x = elems[i].id;
				Post.SendFillIn(x);
				ajaxDone[x] = x;
				weiter = false;
			}
			i++;
		}
	}
}

function downloadClientCryptedFile(id, url, name, mimetype, conditionOnError) {
	Spinner.On();
	var data = '';
	var request = new XMLHttpRequest();
	request.open('GET', url, false);  // synchronous
	request.send(null);
	if (request.status === 200) {
		data = request.responseText;
	}else{
		console.log(request.status);
		Spinner.Off();
		return false;
	}
	if(Cryptography.isEncrypted(data)){
		try {
			data = Cryptography.decrypt(data, clientpassword);
		} catch (err) {
			clientpassword = '';
			condSubmit(conditionOnError, '-1', 'a');
			console.log(err);
			Spinner.Off();
			return false;
		}
	}
	var blob = Cryptography.base64ToBinaryBlob(data, mimetype);
	var url = window.URL.createObjectURL(blob);
	if(document.getElementById(id).nodeName == 'IMG'){
		document.getElementById(id).src = url;
	}else{
		document.getElementById(id).href = url;
		document.getElementById(id).download = name;
		document.getElementById(id).click();
	}
	objectURLs.push(url);
	Spinner.Off();
}

function showFileContentToEdit(isEncrypted) {
	Spinner.On();
	var content = document.getElementById('fileContent').value;
	if (isEncrypted == true) {
		try {
			content = Cryptography.decrypt(content, clientpassword);
		} catch (err) {
			clientpassword = '';
			condSubmit('FS_EDIT_FILE_AFTER_RESET_CLIENT_PW', '-1', 'a');
			console.log(err);
			Spinner.Off();
			return false;
		}
		elementNameToSendCrypted = "editortext__secure__";
	}
	content = Cryptography.base64decode(content);
	document.getElementById('editortext__secure__').value = content;
	document.getElementById('editortext__secure__').scrollTop = 0;
	Spinner.Off();
}

function showFileContentToView(isEncrypted) {
	Spinner.On();
	isFileContentToViewCrypted = isEncrypted;
	document.getElementById("searchtext").onkeyup = filter;
	document.getElementById("searchtext").onreset = filter;
	document.getElementById("searchtext").onsearch = filter;
	var content = document.getElementById('fileContent').value;
	if (isEncrypted == true) {
		try {
			content = Cryptography.decrypt(content, clientpassword);
		} catch (err) {
			clientpassword = '';
			condSubmit('FS_VIEW_FILE_AFTER_RESET_CLIENT_PW', '-1', 'a');
			console.log(err);
			Spinner.Off();
			return false;
		}
	}
	content = Cryptography.base64decode(content);
	content = content.replace('\r\n', '\n');
	var table = document.getElementById('inner_id_ajaxFillInTextFileContent');
	if (table.rows.length > 1) {
		for (var a = table.rows.length - 1; a > 0; a--) {
			table.deleteRow(a);
		}
	}

	var searchstring = escapeHtmlChars(
			document.getElementById("searchtext").value.trim(), false);
	var searchstringparts = searchstring.split(" ");

	var lines = content.split('\n');
	var rowNumber = -1;
	var printContentsUnderHeader = false;
	var hadNonMetaLine = false;
	for ( var lineIndex in lines) {
		var print = true;
		rowNumber++;
		if (hadNonMetaLine == false && lines[lineIndex].trim().length == 0
				|| lines[lineIndex].indexOf('&&META') > -1
				|| lines[lineIndex].indexOf('&&BOF') > -1
				|| lines[lineIndex].indexOf('&&EOF') > -1) {
			print = false;
		} else {
			if (hadNonMetaLine == false && lines[lineIndex].trim().length > 0) {
				hadNonMetaLine = true;
			}
		}
		var bounds = removeBoundIndicators(lines[lineIndex], rowNumber);
		var line = bounds[1];

		for (var p = 0; p < searchstringparts.length; p++) {
			if (searchstringparts[p] !== "") {
				if (line.toLowerCase().indexOf(
						searchstringparts[p].toLowerCase()) == -1) {
					print = false;
					break;
				}
			}
		}

		if (lines[lineIndex].startsWith('==')) {
			printContentsUnderHeader = print;
		}

		if (print == true || printContentsUnderHeader == true) {
			var spanArray = new Array();
			var linkPos = 0;
			var endPos = 0;
			var eol = false;
			do {
				linkPos = line.indexOf('http://', 0);
				if (linkPos < 0) {
					linkPos = line.indexOf('https://', 0);
				}
				if (linkPos > -1) {
					if (linkPos > 0) {
						addSpanToList(spanArray, '',
								line.substring(0, linkPos), searchstringparts);
					}
					endPos = line.indexOf(' ', linkPos + 1);
					if (endPos > 0) {
						addSpanToList(spanArray, 'linkSpan', line.substring(
								linkPos, endPos), searchstringparts);
						line = line.substring(endPos);
					} else {
						addSpanToList(spanArray, 'linkSpan', line
								.substring(linkPos), searchstringparts);
						eol = true;
					}
				} else {
					addSpanToList(spanArray, '', line, searchstringparts);
					eol = true;
				}

			} while (eol == false);

			var row = table.insertRow(table.rows.length);
			var cell = row.insertCell(0);
			cell.className = 'txt';
			var cellContent = '';
			for (var spanIndex = 0; spanIndex < spanArray.length; spanIndex++) {
				var span = document.createElement('span');
				if (spanArray[spanIndex][0].indexOf('linkSpan') > -1) {
					span.linkTarget = spanArray[spanIndex][1];
					span.onclick = function(z) {
						window.open(z.target.linkTarget, '_blank');
					};
				}
				span.className = spanArray[spanIndex][0];
				var text = escapeHtmlChars(spanArray[spanIndex][1], true);
				span.innerHTML = text;
				cellContent += span.outerHTML;
			}
			cell.innerHTML = bounds[0] + cellContent + bounds[2];
		}
	}
	Spinner.Off();
}

function addSpanToList(spanArray, type, value, searchstringparts) {

	var mask = new Array(value.length + 1);
	for (var init = 0; init < value.length; init++) {
		mask[init] = 0;
	}
	mask[value.length] = 9;
	for (var prt = 0; prt < searchstringparts.length; prt++) {
		if (searchstringparts[prt] !== "") {
			var t = 0;
			do {
				t = value.toLowerCase().indexOf(
						searchstringparts[prt].toLowerCase(), t);
				if (t > -1) {
					for (var up = t; up < t + searchstringparts[prt].length; up++) {
						mask[up] = 1;
					}
					t += searchstringparts[prt].length;
				}
			} while (t > -1);
		}
	}
	var state = mask[0];
	var statestart = 0;
	for (var chng = 0; chng < mask.length; chng++) {
		if (mask[chng] != state || mask.length == 1) {
			var s = new Array(2);
			if (state == 1) {
				s[0] = type + " marker";
			} else {
				s[0] = type;
			}
			s[1] = value.substring(statestart, chng);
			spanArray.push(s);
			state = mask[chng];
			statestart = chng;
		}
	}
}

function removeBoundIndicators(line, rowNumber) {
	var bounds = new Array(3);
	bounds[0] = '';
	bounds[2] = '';
	if (line.startsWith('===')) {
		bounds[0] += '<h1>';
		bounds[2] += '</h1>';
		line = line.replace('===', '');
		line = line.replace('===', '');
		line = line.trim();
	}
	if (line.startsWith('==')) {
		bounds[0] += '<h2>';
		bounds[2] += '</h2>';
		line = line.replace('==', '');
		line = line.replace('==', '');
		line = line.trim();
	}
	if (line.startsWith('--small')) {
		bounds[0] += '<small>';
		bounds[2] += '</small>';
		line = line.replace('--small', '');
		line = line.trim();
	}	
	if (line.startsWith('**')) {
		bounds[0] += '<b>';
		bounds[2] += '</b>';
		line = line.replace('**', '');
		line = line.trim();
	}
	if (line.startsWith('*')) {
		bounds[0] += '&bull;';
		line = line.replace('*', '');
		line = line.trim();
	}
	if (line.startsWith('?0') || line.startsWith('?1')) {
		var checkedString = "";
		if (line.startsWith('?1')) {
			checkedString = "checked=\"checked\" ";
		}
		line = line.substring(2);
		var id = rowNumber + "#" + line.replace(/[^a-zA-Z]/g, "");
		var onclick = " onclick=\"" + "setCheckbox('" + id + "')" + "\"";
		bounds[0] = "<div class=\"leftdiv\"><table class=\"notVisible\"><tr>"
				+ "<td class=\"noborderNoxpadding\"><div class=\"check\">"
				+ "<input type=\"checkbox\"" + onclick + " value=\"1\" "
				+ checkedString + "name=\"" + id + "\" id=\"" + id + "\"/>"
				+ "<label for=\"" + id + "\"></label></div></td>"
				+ "<td class=\"noborderNoxpadding\">" + bounds[0];
		bounds[2] += "</td></tr></table></div>";
	}
	bounds[1] = line;
	return bounds;
}

function escapeHtmlChars(unsafe, escapeEmptyLines) {
	var safe = unsafe.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(
			/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
	if (safe.length > 0 && safe.startsWith(" ")) {
		var z = 0;
		for ( var chars in safe) {
			if (safe[chars] == ' ') {
				z++;
			} else {
				break;
			}
		}
		var rplc = "";
		for (var a = 0; a < z; a++) {
			rplc = rplc + "&nbsp";
		}
		line = rplc + safe.substring(z);
	} else if (safe.length == 0 && escapeEmptyLines) {
		safe = safe + "&nbsp";
	}
	return safe;
}

function filter() {
	setTimeout(interval, 30);
}

function interval() {
	var string = document.getElementById("searchtext").value;
	if (processed === string) {
		return;
	}
	if (monitor == true) {
		setTimeout(interval, 30);
		return;
	}
	showFileContentToView(isFileContentToViewCrypted);
	processed = string;
}

function setCheckbox(id) {
	Spinner.On();
	var ids = id.split('#');
	var content = document.getElementById('fileContent').value;
	if (isFileContentToViewCrypted == true) {
		try {
			content = Cryptography.decrypt(content, clientpassword);
		} catch (err) {
			console.log(err);
			Spinner.Off();
			alert('Checkbox-Änderung konnte nicht gespeichert werden.');
			return false;
		}
	}
	content = Cryptography.base64decode(content);
	var lines = content.split('\n');
	var hash = lines[ids[0]].substring(2).replace(/[^a-zA-Z]/g, "");
	if (hash === ids[1]) {
		var newLine = '';
		if (document.getElementById(id).checked == 'checked'
				|| document.getElementById(id).checked == true) {
			newLine += '?1';
		} else {
			newLine += '?0';
		}
		newLine += lines[ids[0]].substring(2);
		lines[ids[0]] = newLine;
		var newText = '';
		for ( var lineIndex in lines) {
			newText += lines[lineIndex] + '\n';
		}
		Post.SendCheckbox(newText);
	} else {
		alert('Fehler beim Speichern der Checkbax! (1)');
		console.log(ids[0] + ':' + hash + ' / ' + ids[1]);
	}

}

function execThis(code) {
	if (window.execScript) {
		window.execScript(code);
	} else {
		window.eval(code);
	}
}

function runScripts() {
	breakScripts = false;
	var arrayElem = document.getElementsByName('scriptnames');
	if (arrayElem != null && arrayElem.length > 0) {
		for (var i = 0; i < arrayElem.length; i++) {
			if (breakScripts == false) {
				execThis(arrayElem[i].value);
			}
		}
	}
	initKeys();
}

function forgetPasswords() {
	clientpassword = '';
	clientpasswordCompare = '';
}

function checkClientSideEncryptionPassword() {
	if (clientpassword === clientpasswordCompare) {
		condSubmit('FS_FILE_ENCRYPT_CLIENT_START', '-1', 'a');
	} else {
		alert('Die eingegebenen Passwörter stimmen nicht überein. Bitte nochmal eingeben.');
		condSubmit('PASSWORD_ASK_ENCRYPT_CLIENT', '-1', 'a');
	}
}

function encryptFileContent() {
	var text = document.getElementById('fileContent').value;
	text = Cryptography.encrypt(text, clientpassword);

	client = new XMLHttpRequest();
	client.onerror = function(e) {
		alert('Fehler beim Verschlüseln! (1)');
		console.log(e);
		Spinner.Off();
	};
	client.onreadystatechange = function() {
		if (client.readyState == 4) {
			if (client.status == 200) {
				condSubmit('FS_FILE_ENCRYPT_CLIENT_END', '-1', 'a');
			} else {
				alert('Fehler beim Verschlüseln! (2)');
				console.log('client.status=' + client.status);
			}
		}
		Spinner.Off();
	};
	var blob = new Blob([ text ], {
		type : "text/plain"
	});
	var formData = new FormData();
	formData.append("convID", document.getElementById('convID').value);
	formData.append("endtoendcrypted[]", blob, 
			document.getElementById('filenameForEncrypt').value);
	formData.append("preventDoubleFilenames", "false");
	formData.append("formTerminator", "true");
	client.open("POST", "/FilesUploadServlet");
	client.send(formData);
}

var Spinner = new Object();
Spinner.On = function() {
	if (document.getElementById('refresh') != null) {
		document.getElementById('refresh').className = refreshClass;
	}
}
Spinner.Off = function() {
	if (document.getElementById('refresh') != null) {
		document.getElementById('refresh').className = '';
	}
}

var Ajax = new Object();
Ajax.Request = function(method, url, query, callback) {
	this.callbackMethod = callback;
	this.request = (window.XMLHttpRequest) ? new XMLHttpRequest()
			: new ActiveXObject("MSXML2.XMLHTTP");
	this.request.onreadystatechange = function() {
		Ajax.checkReadyState();
	};
	if (method.toLowerCase() == 'get')
		url = url + "?" + query;
	this.request.open(method, url, true);
	this.request.setRequestHeader("Content-Type",
			"application/x-www-form-urlencoded");
	this.request.send(query);
};
Ajax.checkReadyState = function(_id) {
	switch (this.request.readyState) {
	case 4:
		this.callbackMethod(this.request.responseText, this.request.status);
	}
};

var Post = new Object();
Post.Display = function(form) {
	var query = Post.buildQuery(form);
	alert(query);
};
Post.Send = function(form) {
	var query = Post.buildQuery(form);
	Ajax.Request(form.method, form.action, query, Post.OnResponse);
};
Post.SendFillIn = function(id) {
	var query = "id=" + id + "&convID="
			+ document.getElementById('convID').value + "&type=fillIn";
	Ajax.Request("POST", "/FilesAjaxServlet", query,
			Post.OnResponseFillIn);
};
Post.SendCheckbox = function(text) {
	var b64 = Cryptography.base64encode(text);
	var toSend = '';
	if (isFileContentToViewCrypted == true) {
		toSend = Cryptography.encrypt(b64, clientpassword);
	} else {
		toSend = b64;
	}
	document.getElementById('fileContent').value = toSend;
	var query = "text=" + encodeURIComponent(toSend) + "&textlength="
			+ toSend.length + "&convID="
			+ document.getElementById('convID').value + "&type=setCheckbox";
	Ajax.Request("POST", "/FilesAjaxServlet", query,
			Post.OnResponseCheckbox);
}
Post.OnResponse = function(xml, status) {
	if (status != 200) {
		alert("Die Anfrage konnte nicht erfolgreich verarbeitet werden.");
		Spinner.Off();
	} else {
		document.getElementsByTagName('body')[0].innerHTML = xml;
		ajaxDone = new Array();
		runScripts();
		window.scrollTo(0, 0);
	}
};
Post.OnResponseFillIn = function(xml, status) {
	if (status == 200) {
		var xmlDoc;
		if (window.DOMParser) {
			parser = new DOMParser();
			xmlDoc = parser.parseFromString(xml, "text/xml");
		} else {
			xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
			xmlDoc.async = false;
			xmlDoc.loadXML(xml);
		}
		for (var i = 0; i < xmlDoc.getElementsByTagName("item").length; i++) {
			var r = xmlDoc.getElementsByTagName("item")[i].getAttribute("name");
			var s = xmlDoc.getElementsByTagName("item")[i].childNodes[0].nodeValue;
			document.getElementById(r).innerHTML = s;
			fillIn();
		}
	}
};
Post.OnResponseCheckbox = function(xml, status) {
	if (status == 200) {
		Spinner.Off();
	} else {
		alert("Fehler beim Speichern der Checkbox! (2)");
		// reload after saving problem
		condSubmit('FS_VIEW_FILE', '-1', '');
	}
};
Post.buildQuery = function(form) {
	var query = "";
	for (var i = 0; i < form.elements.length; i++) {
		var key = form.elements[i].name;
		if (key) {
			query += Post.getElementValue(key, form.elements[i]);
		}
	}
	if (sendFileContent == true) {
		query += "fileContent="
				+ encodeURIComponent(document.getElementById('fileContent').value)
				+ "&";
		sendFileContent = false;
	}
	elementNameToSendCrypted = '';
	var m = +new Date().getTime();
	var nocache = new Number(m).toString().concat("-").concat(
			new Number(Math.random()).toString());
	query += "nocache=" + nocache + "&";
	return query;
};
Post.getElementValue = function(key, formElement) {
	var value = "";
	var append = false;
	if (formElement.length != null)
		var type = formElement[0].type;
	if ((typeof (type) == 'undefined') || (type == 0))
		var type = formElement.type;
	switch (type) {
	case 'undefined':
		append = false;
		break;
	case 'radio':
		if (formElement.checked == true) {
			value = formElement.id;
			append = true;
		}
		break;
	case 'select-multiple':
		var myArray = new Array();
		for (var x = 0; x < formElement.length; x++)
			if (formElement[x].selected == true)
				myArray[myArray.length] = formElement[x].value;
		value = myArray;
		append = true;
		break;
	case 'checkbox':
		value = formElement.checked;
		append = true;
		break;
	default:
		value = formElement.value;
		append = true;
	}
	if (append == true) {
		if (key.indexOf("__secure__") > -1) {
			var b64 = Cryptography.base64encode(value);
			var toSend = '';
			if (key === elementNameToSendCrypted
					&& elementNameToSendCrypted !== '') {
				toSend = Cryptography.encrypt(b64, clientpassword);
			} else {
				toSend = b64;
			}
			var query = key + "=" + encodeURIComponent(toSend) + "&" + key
			+ "__length=" + encodeURIComponent(toSend.length)
			+ "&";
		} else if (key.indexOf("__clientpassword__") > -1
				|| key.indexOf("__hashedpassword__") > -1) {
			if (key.indexOf("__clientpassword__") > -1) {
				if (key.indexOf("one") > -1) {
					clientpassword = value;
				}
				if (key.indexOf("two") > -1) {
					clientpasswordCompare = value;
				}
			}
			if (key.indexOf("__hashedpassword__") > -1) {
				if (key.indexOf("one") > -1) {
					var query = key
							+ "="
							+ encodeURIComponent(Cryptography
									.hashPassword(value, 10)) + "&";
				}
				if (key.indexOf("two") > -1) {
					var query = key
							+ "="
							+ encodeURIComponent(Cryptography
									.hashPassword(value, 10)) + "&";
				}
			}
		} else {
			var query = key + "=" + encodeURIComponent(value) + "&";
		}
		return query;
	} else {
		return "";
	}
};
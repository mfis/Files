var client = null;
var formData = new FormData();
var fileCounter = 0;
var hasServerCryptedFiles = false;

function fileChange() {
	hasServerCryptedFiles = false;
	var myNode = document.getElementById("fileList");
	while (myNode.firstChild) {
		myNode.removeChild(myNode.firstChild);
	}

	var list = document.getElementById("fileA").files;
	if (list && list.length > 0) {
		hasServerCryptedFiles = false;
		for (var i = 0; i < list.length; i++) {
			if (list[i]) {
				var size = Math.round(list[i].size / 1024);
				if (size == 0)
					size = 1;
				var ul = '';
				if (isEndToEndCryptable(list[i])) { // end-to-end
					if (document.getElementById("lifileListE2E") == null) {
						var newLI = document.createElement("LI");
						var newA = document.createElement("A");
						var newText = document
								.createTextNode('Ende-zu-Ende-verschlüsselbar');
						newLI.appendChild(newA);
						var newUL = document.createElement("UL");
						newUL.id = 'ulfileListE2E';
						newLI.appendChild(newUL);
						newA.appendChild(newText);
						newLI.id = "lifileListE2E";
						document.getElementById("fileList").appendChild(newLI);
					}
					ul = 'ulfileListE2E';
					idToAdd = 'lifileListE2E';
				} else { // Server crypted
					hasServerCryptedFiles = true;
					if (document.getElementById("lifileListSvr") == null) {
						hasServerCryptedFiles = true;
						var newLI = document.createElement("LI");
						var newA = document.createElement("A");
						var newText = document
								.createTextNode('Punkt-zu-Punkt-verschlüsselbar');
						newLI.appendChild(newA);
						var newUL = document.createElement("UL");
						newUL.id = 'ulfileListServer';
						newLI.appendChild(newUL);
						newA.appendChild(newText);
						newLI.id = "lifileListSvr";
						document.getElementById("fileList").appendChild(newLI);
					}
					ul = 'ulfileListServer';
					idToAdd = 'lifileListSvr';
				}
				var newLI = document.createElement("LI");
				var newA = document.createElement("A");
				var newText = document.createTextNode(list[i].name + '  ('
						+ size + ' kB)');
				newLI.appendChild(newA);
				newA.appendChild(newText);
				document.getElementById(ul).appendChild(newLI);
			}
		}
		document.getElementById("progress").value = 0;
		document.getElementById("prozent").innerHTML = "";
		document.getElementById("uploadStarten").style.display = "inline";
	} else {
		document.getElementById("progress").innerHTML = "Keine Dateien ausgewählt";
		document.getElementById("uploadStarten").style.display = "none";
	}
	document.getElementById("state").innerHTML = "";
}

function isEndToEndCryptable(file) {
	
	// FIXME: Abfrage auch in FilesFile.java!!
	if ((file.size / 1024) > (1024 * 30)) { // 30 MB
		return false;
	}
	var filenameparts = file.name.split(".");
	var suffix = filenameparts[filenameparts.length-1];
	var clientCryptoFileTypes = document.getElementById("clientCryptoFileTypes").value.split(",");
	for (var i = 0; i < clientCryptoFileTypes.length; i++) {
			if (clientCryptoFileTypes[i].trim().toLowerCase() === suffix.trim().toLowerCase()) {
				return true;
				break;
			}
	}
	return false;
}

function checkPassword() {
	if (document.getElementById("clientpasswordhashedpassworduploadpassone").value
			.trim().length > 0
			&& document
					.getElementById("clientpasswordhashedpassworduploadpasstwo").value
					.trim().length > 0
			&& document
					.getElementById("clientpasswordhashedpassworduploadpassone").value == document
					.getElementById("clientpasswordhashedpassworduploadpasstwo").value) {
		return "==";
	} else if (document
			.getElementById("clientpasswordhashedpassworduploadpassone").value
			.trim().length > 0
			|| document
					.getElementById("clientpasswordhashedpassworduploadpasstwo").value
					.trim().length > 0) {
		return "!=";
	} else {
		return "";
	}
}

function uploadFile() {

	document.getElementById("state").innerHTML = "";
	Spinner.On();
	if (checkPassword() == "!=") {
		document.getElementById("state").innerHTML = "Die Passwort-Eingaben stimmen nicht überein.";
		return false;
	}

	formData = new FormData();
	client = new XMLHttpRequest();

	var prog = document.getElementById("progress");
	prog.value = 0;
	prog.max = 100;

	client.onerror = function(e) {
		document.getElementById("uploadAbbrechen").style.display = "none";
		document.getElementById("progress").innerHTML = "Fehler beim Upload!";
		document.getElementById("uploadStarten").style.display = "none";
		Spinner.Off();
	};

	client.onload = function(e) {
		prog.value = prog.max;
	};

	client.upload.onprogress = function(e) {
		var p = Math.round(100 / e.total * e.loaded);
		document.getElementById("progress").value = p;
		document.getElementById("prozent").innerHTML = p + "% ";
	};

	client.onabort = function(e) {
		document.getElementById("uploadAbbrechen").style.display = "none";
		document.getElementById("uploadStarten").style.display = "none";
		Spinner.Off();
	};

	client.onreadystatechange = function() {
		if (client.readyState == 4) {
			if (client.status == 200) {
				document.getElementById("state").innerHTML = "Fertig! - "
						+ client.responseText;
				Spinner.Off();
			} else {
				document.getElementById("state").innerHTML = "Servlet-Fehler! - "
						+ client.status + " / " + client.responseText;
			}
			document.getElementById("uploadAbbrechen").style.display = "none";
			document.getElementById("uploadStarten").style.display = "none";
		} else if (client.readyState == 3) {
			document.getElementById("state").innerHTML = "Upload läuft...";
		} else {
			document.getElementById("state").innerHTML = "";
		}
	};

	var list = document.getElementById("fileA").files;

	if (list && list.length > 0) {

		formData.append("convID", document.getElementById('convID').value);
		if (hasServerCryptedFiles) {
			formData
					.append(
							"hashedPassword",
							Cryptography
									.hashPassword(document
											.getElementById('clientpasswordhashedpassworduploadpassone').value, 10));
		}
		fileCounter = 0;

		if (checkPassword() == "==") { // encrypted

			for (var i = 0; i < list.length; i++) {
				if (list[i]) {
					if (isEndToEndCryptable(list[i])) { // end-to-end-crypted
						document.getElementById("state").innerHTML = "Verschlüsselung...";
						var file = list[i];
						var reader = new FileReader();
						reader.onload = (function(readFile) {
							return function(e) {
								var b64Data = e.target.result.split(',')[1];
								var crypted = Cryptography
										.encrypt(
												b64Data,
												document
														.getElementById("clientpasswordhashedpassworduploadpassone").value);
								var blob = new Blob([ crypted ], {
									type : "text/plain"
								});
								formData.append("endtoendcrypted[]", blob,
										readFile.name);
								fileCounter++;
								if (fileCounter == list.length) {
									sendForm();
								}
							};
						})(file);
						reader.readAsDataURL(file);
					} else { // server-crypted
						formData.append("servercrypted[]", list[i],
								list[i].name);
						fileCounter++;
					}
				}
			}

		} else { // unencrypted

			for (var i = 0; i < list.length; i++) {
				if (list[i]) {
					formData.append("unencrypted[]", list[i], list[i].name);
					fileCounter++;
				}
			}

		}

		if (fileCounter == list.length) {
			sendForm();
		}
	} else {
		document.getElementById("state").innerHTML = "Erst Dateien auswählen, dann Upload starten.";
		document.getElementById("uploadStarten").style.display = "none";
	}

}

function sendForm() {
	formData.append("formTerminator", "true");
	document.getElementById("uploadAbbrechen").style.display = "inline";
	client.open("POST", "/FilesUploadServlet");
	client.send(formData);
}

function uploadAbort() {
	if (client instanceof XMLHttpRequest) {
		client.abort();
		document.getElementById("uploadAbbrechen").style.display = "none";
		document.getElementById("uploadStarten").style.display = "none";
	}
}

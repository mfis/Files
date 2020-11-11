// Uses crypto-js-aes-v3.1.6.js

var base64catalog = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/';
var hexcatalog = '0123456789abcdef';
var PREFIX_CRYPTOJS_AES256_V1 = '#_CJS_CRYPTED_AES256_V1_#';

var Cryptography = new Object();

Cryptography.isEncrypted = function(content) {
	return content.startsWith(PREFIX_CRYPTOJS_AES256_V1);
}

Cryptography.encrypt = function(text, pass) {

	if (pass == '') {
		alert('Fehler: Kein Passwort gesetzt!');
		throw 'no pw set #1';
	}
	pass = Cryptography.hashPassword(pass, 3);
	var pos = 0;
	var blocksize = 1024 * 1024; // 1 MB
	var len = text.length;
	var ciphers = new Array();
	var more = true;
	while (more == true) {
		var block = '';
		if ((pos + blocksize) > len) {
			block = text.substring(pos);
			more = false;
		} else {
			block = text.substring(pos, pos + blocksize);
			pos += blocksize;
		}
		var aes = CryptoJS.AES.encrypt(block, pass);
		if (aes.key.toString().length * 4 != 256) {
			alert('Verschlüsselung unsicher: Falsche AES-Schlüssellänge!');
			throw 'wrong aes-keysize:' + aes.key.toString().length * 4;
		}
		ciphers.push(aes.toString());
	}
	var hmacstring = hmac(ciphers[0], pass);

	return PREFIX_CRYPTOJS_AES256_V1 + hmacstring + ciphers.toString();
}

Cryptography.decrypt = function(crypted, pass) {

	if (pass == '') {
		alert('Fehler: Kein Passwort gesetzt!');
		throw 'no pw set #1';
	}
	pass = Cryptography.hashPassword(pass, 3);

	if (!crypted.startsWith(PREFIX_CRYPTOJS_AES256_V1)) {
		alert('Fehler: Prefix nicht definiert!');
		throw 'no prefix set #1';
	}
	var transithmac = crypted.substring(PREFIX_CRYPTOJS_AES256_V1.length,
			PREFIX_CRYPTOJS_AES256_V1.length + 64);
	var crypted = crypted.substring(PREFIX_CRYPTOJS_AES256_V1.length + 64);
	var ciphers = crypted.split(',');

	var decryptedhmac = hmac(ciphers[0], pass);
	if (decryptedhmac != transithmac) {
		throw ('hmac unequal');
	}

	var plaintext = '';
	for (var i = 0; i < ciphers.length; i++) {
		var block = CryptoJS.AES.decrypt(ciphers[i], pass);
		plaintext += block.toString(CryptoJS.enc.Utf8);
	}

	return plaintext;
}

Cryptography.base64encode = function(text) {
	var encoded = CryptoJS.enc.Base64.stringify(CryptoJS.enc.Utf8.parse(text));
	return encoded;
}

Cryptography.base64decode = function(encoded) {
	var decoded = CryptoJS.enc.Base64.parse(encoded)
			.toString(CryptoJS.enc.Utf8);
	return decoded;
}

Cryptography.base64ToBinaryBlob = function(b64Data, contentType, sliceSize) {
	contentType = contentType || '';
	sliceSize = sliceSize || 512;
	var byteCharacters = atob(b64Data);
	var byteArrays = [];
	for (var offset = 0; offset < byteCharacters.length; offset += sliceSize) {
		var slice = byteCharacters.slice(offset, offset + sliceSize);
		var byteNumbers = new Array(slice.length);
		for (var i = 0; i < slice.length; i++) {
			byteNumbers[i] = slice.charCodeAt(i);
		}
		var byteArray = new Uint8Array(byteNumbers);
		byteArrays.push(byteArray);
	}
	var blob = new Blob(byteArrays, {
		type : contentType
	});
	return blob;
}

function offset(s, cat) {
	var o = 0;
	for (var i = 0; i < s.length; i++) {
		o += s.charCodeAt(i);
	}
	var m = o % cat.length;
	if (m == 0) {
		m = s.length
	}
	return m;
}

function shiftHex(s, o) {
	var n = '';
	for (var i = 0; i < s.length; i++) {
		var j = hexcatalog.indexOf(s.substring(i, i + 1));
		if (j == -1)
			throw 'unknown char ' + s.substring(i, i + 1);
		var x = j + o;
		if (x < 0) {
			x = hexcatalog.length + x;
		} else if (x > 15) {
			x = x - hexcatalog.length;
		}
		n += hexcatalog.substring(x, x + 1);
	}
	return n;
}

function hmac(ciphertextstring, pass) {
	var sha256pass = pass = Cryptography.hashPassword(pass, 3);
	var offsetSha256pass = offset(sha256pass, hexcatalog);
	var hmacstring = '';
	if (ciphertextstring.length > 8192) {
		hmacstring = ciphertextstring.substring(0, 8192);
	} else {
		hmacstring = ciphertextstring;
	}
	for (var i = 0; i < 5; ++i) {
		hmacstring = CryptoJS.HmacSHA256(hmacstring, sha256pass).toString();
		hmacstring = shiftHex(hmacstring, offsetSha256pass);
	}
	return hmacstring;
}

function log(msg) {
	var time = new Date();
	console.log(time.getHours() + ":" + time.getMinutes() + ":"
			+ time.getSeconds() + "." + time.getMilliseconds() + " - " + msg);
}

Cryptography.hashPassword = function(pass, rounds) {
	var offsetPass = offset(pass, hexcatalog);
	var sha256pass = pass;
	for (var i = 0; i < rounds; ++i) {
		sha256pass = CryptoJS.SHA256(sha256pass).toString();
		sha256pass = shiftHex(sha256pass, offsetPass);
	}
	return sha256pass;
}